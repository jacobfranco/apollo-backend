package com.apollo.backendapi.pojos;

import com.apollo.backendapi.CustomInstantDeserializer;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = true)
public class PostLiveLolMatchSummary {
    public String channel;
    public String uuid;

    public Instant created;

    public PostLiveLolPayload payload;
}
