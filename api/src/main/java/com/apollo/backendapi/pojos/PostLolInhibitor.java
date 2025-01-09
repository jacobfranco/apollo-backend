package com.apollo.backendapi.pojos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class PostLolInhibitor {
    public boolean standing;
    public PostLolMatchClock respawn_time;
}