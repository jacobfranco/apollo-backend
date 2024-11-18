package com.apollo.backendapi.pojos;

import com.apollo.backend.data.LolTeamAggStats;

public class GetTeamAggStats {
    public int totalMatches;
    public int totalWins;
    public int totalLosses;
    public int totalScore;
    public int totalGoldEarned;
    public int totalTurretsDestroyed;
    public int totalInhibitorsDestroyed;
    public double averageScore;
    public double averageGoldEarned;
    public double averageTurretsDestroyed;
    public double averageInhibitorsDestroyed;
    public int currentWinStreak;

    public GetTeamAggStats(LolTeamAggStats stats) {
        this.totalMatches = stats.getTotalMatches();
        this.totalWins = stats.getTotalWins();
        this.totalLosses = stats.getTotalLosses();
        this.totalScore = stats.getTotalScore();
        this.totalGoldEarned = stats.getTotalGoldEarned();
        this.totalTurretsDestroyed = stats.getTotalTurretsDestroyed();
        this.totalInhibitorsDestroyed = stats.getTotalInhibitorsDestroyed();
    }
}
