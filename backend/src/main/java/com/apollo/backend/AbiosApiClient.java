package com.apollo.backend;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;
import java.io.IOException;

public class AbiosApiClient {
    private static final String BASE_URL = "https://atlas.abiosgaming.com/v3/";
    private final String apiSecret;
    private final HttpClient httpClient;

    public AbiosApiClient(String apiSecret) {
        this.apiSecret = apiSecret;
        this.httpClient = HttpClient.newHttpClient();
    }

    public String getSeries(String filter, String order, int skip, int take) throws IOException, InterruptedException {
        return makeRequest("series", filter, order, skip, take);
    }

    public String getPlayers(String filter, String order, int skip, int take) throws IOException, InterruptedException {
        return makeRequest("players", filter, order, skip, take);
    }

    private String makeRequest(String endpoint, String filter, String order, int skip, int take) throws IOException, InterruptedException {
        String queryParams = String.format("?filter=%s&order=%s&skip=%d&take=%d", 
            filter, order, skip, take);
        
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(BASE_URL + endpoint + queryParams))
            .header("Abios-Secret", apiSecret)
            .GET()
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        System.out.println("Making api request to: " + BASE_URL + endpoint + queryParams);
        return response.body();
    }
}