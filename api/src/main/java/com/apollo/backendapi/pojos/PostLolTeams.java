package com.apollo.backendapi.pojos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class PostLolTeams {
    public PostLolTeamSummary home;
    public PostLolTeamSummary away;
}