package com.apollo.backendapi.pojos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

public class PostParticipant {
    public int seed;
    public int score;
    public boolean forfeit;
    public PostRoster roster;
    public boolean winner;
    public PostParticipantStats stats;
}