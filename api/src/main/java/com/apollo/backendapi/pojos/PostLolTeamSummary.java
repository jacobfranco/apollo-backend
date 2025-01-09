package com.apollo.backendapi.pojos;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class PostLolTeamSummary {
    public PostLolRoster roster;
    public int score;
    public boolean is_winner;
    public int gold_earned;
    public int turrets_destroyed;
    public int inhibitors_destroyed;
    public PostLolFaction faction;
    public PostLolStructures structures;
    public PostLolCreeps creeps;
    public List<PostLolPlayerSummary> players;
}