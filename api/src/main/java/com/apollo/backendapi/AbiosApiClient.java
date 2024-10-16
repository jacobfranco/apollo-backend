package com.apollo.backendapi;

import okhttp3.*;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class AbiosApiClient {
    private static final String BASE_URL = "https://atlas.abiosgaming.com/v3/";
    private static final String WEBSOCKET_URL = "wss://hermes.abiosgaming.com/subscribe";
    private final String apiSecret;
    private final OkHttpClient httpClient;
    private WebSocket webSocket;
    private Map<String, String> lastResponseHeaders;
    private static final int MAX_RETRIES = 3;
    private static final long WEBSOCKET_RETRY_DELAY_MS = 5000; // 5 seconds
    private static final int MAX_WEBSOCKET_RETRIES = 5;
    private final AtomicBoolean isWebSocketConnecting = new AtomicBoolean(false);
    private WebSocketListener currentWebSocketListener;
    private List<String> lastChannels;
    private Map<String, String> lastFilters;
    private String lastQueue;

    public AbiosApiClient(String apiSecret) {
        this.apiSecret = apiSecret;
        this.lastResponseHeaders = new HashMap<>();

        // Configure OkHttp client
        this.httpClient = new OkHttpClient.Builder()
                .connectionPool(new ConnectionPool(10, 5, TimeUnit.MINUTES))
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .protocols(java.util.Arrays.asList(Protocol.HTTP_2, Protocol.HTTP_1_1))
                .retryOnConnectionFailure(true)
                .addInterceptor(this::retryingIntercept)
                .build();
    }

    private Response retryingIntercept(Interceptor.Chain chain) throws IOException {
        Request request = chain.request();
        Response response = null;
        IOException lastException = null;

        for (int retryCount = 0; retryCount <= MAX_RETRIES; retryCount++) {
            try {
                if (response != null) {
                    response.close();
                }

                response = chain.proceed(request);
                storeHeaders(response.headers());

                if (response.isSuccessful()) {
                    return response;
                }

                if (response.code() == 429) {
                    String resetHeader = response.header("X-RateLimit-Reset");
                    if (resetHeader != null) {
                        try {
                            long sleepMillis = Long.parseLong(resetHeader);
                            Thread.sleep(sleepMillis);
                            continue;
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new IOException("Interrupted while waiting for rate limit", e);
                        }
                    }
                }

                throw new IOException("Unexpected response code: " + response.code());
            } catch (IOException e) {
                lastException = e;
                if (retryCount < MAX_RETRIES && shouldRetry(e)) {
                    try {
                        long sleepMillis = (long) Math.pow(2, retryCount) * 1000;
                        Thread.sleep(sleepMillis);
                        continue;
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new IOException("Interrupted while retrying request", ie);
                    }
                }
                throw e;
            }
        }

        throw lastException != null ? lastException : new IOException("Failed after " + MAX_RETRIES + " retries");
    }

    private boolean shouldRetry(IOException e) {
        String message = e.getMessage();
        if (message == null)
            return false;

        return message.contains("GOAWAY") ||
                message.contains("connection reset") ||
                message.contains("connection refused") ||
                message.contains("Connection timed out");
    }

    private void storeHeaders(Headers headers) {
        lastResponseHeaders.clear();
        for (int i = 0; i < headers.size(); i++) {
            lastResponseHeaders.put(headers.name(i), headers.value(i));
        }
    }

    private String makeRequest(String endpoint, String filter, String order, int skip, int take)
            throws IOException {
        StringBuilder queryParams = new StringBuilder("?");
        if (filter != null && !filter.isEmpty()) {
            queryParams.append("filter=").append(URLEncoder.encode(filter, StandardCharsets.UTF_8)).append("&");
        }
        if (order != null && !order.isEmpty()) {
            queryParams.append("order=").append(URLEncoder.encode(order, StandardCharsets.UTF_8)).append("&");
        }
        queryParams.append("skip=").append(skip).append("&take=").append(take);

        String url = BASE_URL + endpoint + queryParams.toString();
        System.out.println("Making api request to: " + url);

        Request request = new Request.Builder()
                .url(url)
                .header("Abios-Secret", apiSecret)
                .get()
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            return response.body().string();
        }
    }

    // WebSocket implementation with retry mechanism
    public void connectToWebSocket(List<String> channels, Map<String, String> filters, String queue) {
        if (isWebSocketConnecting.get()) {
            System.out.println("WebSocket connection attempt already in progress");
            return;
        }

        this.lastChannels = channels;
        this.lastFilters = filters;
        this.lastQueue = queue;

        connectWebSocketWithRetry(0);
    }

    private void connectWebSocketWithRetry(int retryCount) {
        if (retryCount >= MAX_WEBSOCKET_RETRIES) {
            System.err.println("Max WebSocket reconnection attempts reached");
            return;
        }

        if (!isWebSocketConnecting.compareAndSet(false, true)) {
            return;
        }

        StringBuilder urlBuilder = new StringBuilder(WEBSOCKET_URL);
        urlBuilder.append("?secret=").append(URLEncoder.encode(apiSecret, StandardCharsets.UTF_8));

        for (String channel : lastChannels) {
            urlBuilder.append("&channel=").append(URLEncoder.encode(channel, StandardCharsets.UTF_8));
        }

        if (lastFilters != null && !lastFilters.isEmpty()) {
            for (Map.Entry<String, String> filterEntry : lastFilters.entrySet()) {
                urlBuilder.append("&filter=")
                        .append(URLEncoder.encode(filterEntry.getKey() + "=" + filterEntry.getValue(),
                                StandardCharsets.UTF_8));
            }
        }

        if (lastQueue != null && !lastQueue.isEmpty()) {
            urlBuilder.append("&queue=").append(URLEncoder.encode(lastQueue, StandardCharsets.UTF_8));
        }

        Request request = new Request.Builder()
                .url(urlBuilder.toString())
                .build();

        currentWebSocketListener = new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                System.out.println("WebSocket connection opened");
                isWebSocketConnecting.set(false);
            }

            @Override
            public void onMessage(WebSocket webSocket, String text) {
                ApolloApiController.manager.handleIncomingMessage(text);
            }

            @Override
            public void onClosing(WebSocket webSocket, int code, String reason) {
                System.out.println("WebSocket closing: " + reason);
                webSocket.close(1000, null);
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                System.err.println("WebSocket error: " + t.getMessage());
                webSocket.cancel();
                isWebSocketConnecting.set(false);

                // Schedule reconnection attempt
                new Thread(() -> {
                    try {
                        Thread.sleep(WEBSOCKET_RETRY_DELAY_MS);
                        System.out.println("Attempting WebSocket reconnection (attempt " + (retryCount + 1) + ")");
                        connectWebSocketWithRetry(retryCount + 1);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }).start();
            }

            @Override
            public void onClosed(WebSocket webSocket, int code, String reason) {
                System.out.println("WebSocket closed: " + reason);
                isWebSocketConnecting.set(false);
            }
        };

        webSocket = httpClient.newWebSocket(request, currentWebSocketListener);
    }

    // All your existing API methods
    public String getSeries(String filter, String order, int skip, int take) throws IOException {
        return makeRequest("series", filter, order, skip, take);
    }

    public String getMatchesForSeries(int seriesId, String filter, String order, int skip, int take)
            throws IOException {
        String endpoint = "series/" + seriesId + "/matches";
        return makeRequest(endpoint, filter, order, skip, take);
    }

    public String getMatchRosters(int matchId) throws IOException {
        return makeRequest("matches/" + matchId + "/rosters", null, null, 0, 50);
    }

    public String getSeriesRosters(int seriesId) throws IOException {
        return makeRequest("series/" + seriesId + "/rosters", null, null, 0, 50);
    }

    public String getTeam(int teamId) throws IOException {
        return makeRequest("teams/" + teamId, null, null, 0, 1);
    }

    public String getPlayer(int playerId) throws IOException {
        return makeRequest("players/" + playerId, null, null, 0, 1);
    }

    public String getTeams(String filter, String order, int skip, int take) throws IOException {
        return makeRequest("teams", filter, order, skip, take);
    }

    public String getPlayers(String filter, String order, int skip, int take) throws IOException {
        return makeRequest("players", filter, order, skip, take);
    }

    public String getMatchSummary(int matchId) throws IOException {
        return makeRequest("matches/" + matchId + "/live/cv/summary", null, null, 0, 1);
    }

    public String getAssets(String filter, String order, int skip, int take) throws IOException {
        return makeRequest("assets", filter, order, skip, take);
    }

    public String getTournaments(String filter, String order, int skip, int take) throws IOException {
        return makeRequest("tournaments", filter, order, skip, take);
    }

    public String getSubstages(String filter, String order, int skip, int take) throws IOException {
        return makeRequest("substages", filter, order, skip, take);
    }

    public String getCasters(String filter, String order, int skip, int take) throws IOException {
        return makeRequest("casters", filter, order, skip, take);
    }

    public Map<String, String> getLastResponseHeaders() {
        return new HashMap<>(lastResponseHeaders);
    }

    public void closeWebSocket() {
        if (webSocket != null) {
            webSocket.close(1000, "Closing connection");
        }
    }

    public void shutdown() {
        closeWebSocket();
        httpClient.dispatcher().executorService().shutdown();
        httpClient.connectionPool().evictAll();
    }
}