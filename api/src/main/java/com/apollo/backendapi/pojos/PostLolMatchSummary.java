package com.apollo.backendapi.pojos;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class PostLolMatchSummary {
    public int id;
    public PostLolTeams teams;
    public PostLolPits pits;
    public long latest_events_channel_index;
    public long latest_states_channel_index;

    public Instant timestamp;

    public PostLolMatch match;
}
