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
    public int totalDragonKills;
    public int totalBaronKills;
    public int totalHeraldKills;
    public int totalVoidGrubKills;
    public double averageDragonKills;
    public double averageBaronKills;
    public double averageHeraldKills;
    public double averageVoidGrubKills;
    public int totalSeries;
    public int totalSeriesWins;
    public int totalSeriesLosses;
    public int totalWinRate;
    public int seriesWinRate;

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
        this.totalDragonKills = stats.getTotalDragonKills();
        this.totalBaronKills = stats.getTotalBaronKills();
        this.totalHeraldKills = stats.getTotalHeraldKills();
        this.totalVoidGrubKills = stats.getTotalVoidGrubKills();
        this.averageDragonKills = stats.getAverageDragonKills();
        this.averageBaronKills = stats.getAverageBaronKills();
        this.averageHeraldKills = stats.getAverageHeraldKills();
        this.averageVoidGrubKills = stats.getAverageVoidGrubKills();
        this.totalSeries = stats.getTotalSeries();
        this.totalSeriesWins = stats.getTotalSeriesWins();
        this.totalSeriesLosses = stats.getTotalSeriesLosses();
        this.totalWinRate = stats.getTotalWins() / (stats.getTotalMatches() == 0 ? 1 : stats.getTotalMatches());
        this.seriesWinRate = stats.getTotalSeriesWins() / (stats.getTotalSeries() == 0 ? 1 : stats.getTotalSeries());
    }
}
