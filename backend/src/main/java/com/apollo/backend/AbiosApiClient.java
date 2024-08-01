package com.apollo.backend;

import org.asynchttpclient.*;
import org.asynchttpclient.netty.NettyResponse;
import java.util.concurrent.CompletableFuture;
import java.util.Map;

public class AbiosApiClient {
    private final AsyncHttpClient client;
    private final String apiKey;

    public AbiosApiClient(AsyncHttpClient client, String apiKey) {
        this.client = client;
        this.apiKey = apiKey;
    }

    public CompletableFuture<String> getSeries(Map<String, String> params) {
        String url = "https://atlas.abiosgaming.com/v3/series";
        RequestBuilder request = client.prepareGet(url)
            .addQueryParam("filter", params.get("filter"))
            .addQueryParam("order", params.get("order"))
            .addQueryParam("skip", params.get("skip"))
            .addQueryParam("take", params.get("take"))
            .addHeader("Abios-Secret", apiKey);
        return request.execute()
            .toCompletableFuture()
            .thenApply(NettyResponse::getResponseBody);
    }

    // Add other API methods here as needed
}