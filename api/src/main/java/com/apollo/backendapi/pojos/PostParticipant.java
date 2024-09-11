package com.apollo.backendapi.pojos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class PostParticipant {
    public int seed;
    public int score;
    public boolean forfeit;
    @JsonProperty("roster.id")
    public int rosterId;
    public boolean winner;
    public PostParticipantStats stats;
}