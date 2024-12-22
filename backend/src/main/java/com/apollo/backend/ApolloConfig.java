package com.apollo.backend;

public class ApolloConfig {
    public static String API_URL = System.getProperty("backend.api.url", "http://localhost:8080");
    public static String API_WEB_SOCKET_URL = System.getProperty("backend.api.web.socket.url", "ws://localhost:8080");
    public static String API_DOMAIN = System.getProperty("backend.api.domain", "localhost");
    public static String FRONTEND_URL = System.getProperty("backend.frontend.url", "http://localhost:3036");
}