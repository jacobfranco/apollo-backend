package com.apollo.backendapi.pojos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class PostLolPit {
    public PostLolNpc npc;
    public String npc_status;
    public PostLolMatchClock empty_since_time;
    public PostLolMatchClock spawn_time;
}