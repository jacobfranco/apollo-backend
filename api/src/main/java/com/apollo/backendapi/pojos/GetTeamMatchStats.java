package com.apollo.backendapi.pojos;

import com.apollo.backend.data.LolTeamSummary;

public class GetTeamMatchStats {
    public int score;
    public boolean isWinner;
    public int goldEarned;
    public int turretsDestroyed;
    public int inhibitorsDestroyed;
    public GetLolFaction faction;
    public GetLolStructures structures;
    public GetLolCreeps creeps;

    // Update the constructor to accept GetLolTeamSummary
    public GetTeamMatchStats(GetLolTeamSummary teamSummary) {
        this.score = teamSummary.score;
        this.isWinner = teamSummary.isWinner;
        this.goldEarned = teamSummary.goldEarned;
        this.turretsDestroyed = teamSummary.turretsDestroyed;
        this.inhibitorsDestroyed = teamSummary.inhibitorsDestroyed;
        this.faction = teamSummary.faction;
        this.structures = teamSummary.structures;
        this.creeps = teamSummary.creeps;
    }
}
