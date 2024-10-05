package com.apollo.backendapi.pojos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = true)
public class PostLiveLolMatchSummary {
    public String channel;
    public String uuid;

    public Instant created;

    public PostLiveLolPayload payload;
}
