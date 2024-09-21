package com.apollo.backendapi.pojos;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class PostLolPlayerSummary {
    public int id;
    public int ui_index;
    public PostLolChampion champion;
    public PostLolKills kills;
    public PostLolAssists assists;
    public PostLolDeaths deaths;
    public PostLolRevives revives;
    public List<PostLolMultiKill> multi_kills;
    public List<Integer> kill_streaks;
    public PostLolItems items;
    public List<PostLolSummonerSpell> summoner_spells;
    public PostLolCreeps creeps;
    public PostLolKeystone keystone;
    public PostLolPosition position;
}