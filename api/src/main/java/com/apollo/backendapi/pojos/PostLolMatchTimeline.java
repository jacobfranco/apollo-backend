package com.apollo.backendapi.pojos;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = true)
public class PostLolMatchTimeline {
    public String phase;

    public Instant start;

    public Instant end;

    public PostLolMatchClock clock;
}