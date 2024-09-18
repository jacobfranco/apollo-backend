package com.apollo.backendapi.pojos;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public class PostRoster {
    public int id;
    public PostTeamInfo team;
    @JsonProperty("line_up")
    public PostLineUp lineUp;
    public PostGameInfo game;

    public static class PostTeamInfo {
        public int id;
    }

    public static class PostLineUp {
        public int id;
        public List<PostPlayerInfo> players;
    }

    public static class PostPlayerInfo {
        public int id;
    }

    public static class PostGameInfo {
        public int id;
    }
}
