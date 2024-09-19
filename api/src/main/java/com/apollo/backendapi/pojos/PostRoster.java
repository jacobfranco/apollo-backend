package com.apollo.backendapi.pojos;

import com.fasterxml.jackson.annotation.JsonProperty;

public class PostRoster {
    public int id;
    public PostTeamInfo team;

    @JsonProperty("line_up")
    public PostLineUp lineUp;

    public PostGameInfo game;
}