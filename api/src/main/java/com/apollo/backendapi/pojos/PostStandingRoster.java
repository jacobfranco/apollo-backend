package com.apollo.backendapi.pojos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = true)
public class PostStandingRoster {
    public int id;
    public Instant from;
    public Instant to;
    public PostRoster roster;
    @JsonProperty("deleted_at")
    public Instant deletedAt;
}