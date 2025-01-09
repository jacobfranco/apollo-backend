package com.apollo.backendapi.pojos;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class PostCaster {
    public Integer id;
    public String displayName;
    public String username;
    public PostGame game;
    public Instant deletedAt;
    public PostStreamingPlatform platform;
    public PostStream stream;
    public PostRegion region;
}