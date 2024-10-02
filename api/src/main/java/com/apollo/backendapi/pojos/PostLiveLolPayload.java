package com.apollo.backendapi.pojos;

import com.apollo.backendapi.CustomInstantDeserializer;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = true)
public class PostLiveLolPayload {
    public int index;

    public Instant timestamp;

    public PostLolMatch match;

    @JsonProperty("event_type")
    public String eventType;

    @JsonProperty("event_data")
    public PostLolMatchSummary eventData;
}
