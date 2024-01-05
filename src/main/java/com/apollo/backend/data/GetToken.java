package com.apollo.backend.data;

public class GetToken {

    private String accessToken;
    private String tokenType = "Bearer";
    private String scope;
    private long createdAt;

    public GetToken() { }

    public GetToken(String accessToken, String scope) {
        this.accessToken = accessToken;
        this.scope = scope;
        this.createdAt = System.currentTimeMillis() / 1000;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public String getTokenType() {
        return tokenType;
    }

    public String getScope() {
        return scope;
    }

    public long getCreatedAt() {
        return createdAt;
    }
}
