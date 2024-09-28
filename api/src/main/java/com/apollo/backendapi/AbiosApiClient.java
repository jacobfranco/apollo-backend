package com.apollo.backendapi;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.net.URI;
import java.net.URLEncoder;
import java.io.IOException;
import java.util.Map;
import java.util.HashMap;
import java.util.List;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

public class AbiosApiClient {
    private static final String BASE_URL = "https://atlas.abiosgaming.com/v3/";
    private static final String WEBSOCKET_URL = "wss://hermes.abiosgaming.com/subscribe";
    private final String apiSecret;
    private final HttpClient httpClient;
    private final OkHttpClient webSocketClient;
    private WebSocket webSocket;
    private Map<String, String> lastResponseHeaders;

    public AbiosApiClient(String apiSecret) {
        this.apiSecret = apiSecret;
        this.httpClient = HttpClient.newHttpClient();
        this.webSocketClient = new OkHttpClient();
        this.lastResponseHeaders = new HashMap<>();
    }

    public String getSeries(String filter, String order, int skip, int take) throws IOException, InterruptedException {
        return makeRequest("series", filter, order, skip, take);
    }

    public String getMatchesForSeries(int seriesId, String filter, String order, int skip, int take)
            throws IOException, InterruptedException {
        String endpoint = "series/" + seriesId + "/matches";
        return makeRequest(endpoint, filter, order, skip, take);
    }

    public String getMatchRosters(int matchId) throws IOException, InterruptedException {
        return makeRequest("matches/" + matchId + "/rosters", null, null, 0, 50);
    }

    public String getSeriesRosters(int seriesId) throws IOException, InterruptedException {
        return makeRequest("series/" + seriesId + "/rosters", null, null, 0, 50);
    }

    public String getTeam(int teamId) throws IOException, InterruptedException {
        return makeRequest("teams/" + teamId, null, null, 0, 1);
    }

    public String getPlayer(int playerId) throws IOException, InterruptedException {
        return makeRequest("players/" + playerId, null, null, 0, 1);
    }

    public String getTeams(String filter, String order, int skip, int take) throws IOException, InterruptedException {
        return makeRequest("teams", filter, order, skip, take);
    }

    public String getPlayers(String filter, String order, int skip, int take) throws IOException, InterruptedException {
        return makeRequest("players", filter, order, skip, take);
    }

    public String getMatchSummary(int matchId) throws IOException, InterruptedException {
        return makeRequest("matches/" + matchId + "/live/cv/summary", null, null, 0, 1);
    }

    public String getAssets(String filter, String order, int skip, int take) throws IOException, InterruptedException {
        return makeRequest("assets", filter, order, skip, take);
    }

    private String makeRequest(String endpoint, String filter, String order, int skip, int take)
            throws IOException, InterruptedException {
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

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Abios-Secret", apiSecret)
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        // Store the headers
        this.lastResponseHeaders.clear();
        response.headers().map().forEach((key, values) -> this.lastResponseHeaders.put(key, values.get(0)));

        return response.body();
    }

    public Map<String, String> getLastResponseHeaders() {
        return new HashMap<>(this.lastResponseHeaders);
    }

    // Hermes

    // Method to establish a WebSocket connection to specified channels with filters
    // and a queue
    public void connectToWebSocket(List<String> channels, Map<String, String> filters, String queue) {
        StringBuilder urlBuilder = new StringBuilder(WEBSOCKET_URL);
        urlBuilder.append("?secret=").append(URLEncoder.encode(apiSecret, StandardCharsets.UTF_8));

        // Add channels to the URL
        for (String channel : channels) {
            urlBuilder.append("&channel=").append(URLEncoder.encode(channel, StandardCharsets.UTF_8));
        }

        // Add filters to the URL
        if (filters != null && !filters.isEmpty()) {
            for (Map.Entry<String, String> filterEntry : filters.entrySet()) {
                String filterKey = filterEntry.getKey();
                String filterValue = filterEntry.getValue();
                urlBuilder.append("&filter=")
                        .append(URLEncoder.encode(filterKey + "=" + filterValue, StandardCharsets.UTF_8));
            }
        }

        // Add queue parameter to the URL
        if (queue != null && !queue.isEmpty()) {
            urlBuilder.append("&queue=").append(URLEncoder.encode(queue, StandardCharsets.UTF_8));
        }

        Request request = new Request.Builder()
                .url(urlBuilder.toString())
                .build();

        this.webSocket = webSocketClient.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                System.out.println("WebSocket connection opened.");
            }

            @Override
            public void onMessage(WebSocket webSocket, String text) {
                // Pass incoming messages to ApolloApiManager for processing
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
                // Reconnection logic can be implemented here if necessary
            }
        });
    }

    // Method to close the WebSocket connection gracefully
    public void closeWebSocket() {
        if (webSocket != null) {
            webSocket.close(1000, "Closing connection");
        }
    }

}