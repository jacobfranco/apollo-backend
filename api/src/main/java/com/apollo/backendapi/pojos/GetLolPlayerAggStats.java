package com.apollo.backendapi.pojos;

import com.apollo.backend.data.LolPlayerAggStats;

public class GetLolPlayerAggStats {
    public int id;
    public int totalMatches;
    public int totalKills;
    public int totalDeaths;
    public int totalAssists;
    public double averageKills;
    public double averageDeaths;
    public double averageAssists;

    public GetLolPlayerAggStats(LolPlayerAggStats stats) {
        this.id = stats.getId();
        this.totalMatches = stats.getTotalMatches();
        this.totalKills = stats.getTotalKills();
        this.totalDeaths = stats.getTotalDeaths();
        this.totalAssists = stats.getTotalAssists();
        this.averageKills = stats.getAverageKills();
        this.averageDeaths = stats.getAverageDeaths();
        this.averageAssists = stats.getAverageAssists();
    }
}
