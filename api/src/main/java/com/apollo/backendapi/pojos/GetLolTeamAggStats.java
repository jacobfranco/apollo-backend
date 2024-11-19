package com.apollo.backendapi.pojos;

import com.apollo.backend.data.LolTeamAggStats;

public class GetLolTeamAggStats {
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

    public GetLolTeamAggStats(LolTeamAggStats stats) {
        this.totalMatches = stats.getTotalMatches();
        this.totalWins = stats.getTotalWins();
        this.totalLosses = stats.getTotalLosses();
        this.totalScore = stats.getTotalScore();
        this.totalGoldEarned = stats.getTotalGoldEarned();
        this.totalTurretsDestroyed = stats.getTotalTurretsDestroyed();
        this.totalInhibitorsDestroyed = stats.getTotalInhibitorsDestroyed();
        this.averageScore = stats.getAverageScore();
        this.averageGoldEarned = stats.getAverageGoldEarned();
        this.averageTurretsDestroyed = stats.getAverageTurretsDestroyed();
        this.averageInhibitorsDestroyed = stats.getAverageInhibitorsDestroyed();
        this.currentWinStreak = stats.getCurrentWinStreak();
    }
}
