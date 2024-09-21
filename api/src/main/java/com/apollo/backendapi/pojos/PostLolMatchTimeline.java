package com.apollo.backendapi.pojos;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = true)
public class PostLolMatchTimeline {
    public String phase;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
    public Instant start;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
    public Instant end;

    public PostLolMatchClock clock;
}