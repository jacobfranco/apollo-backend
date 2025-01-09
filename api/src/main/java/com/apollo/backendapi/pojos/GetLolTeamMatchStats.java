package com.apollo.backendapi.pojos;

import java.time.Instant;
import java.util.Map;

import com.apollo.backend.data.LolTeamSummary;

public class GetLolTeamMatchStats {
    public int matchId;
    public Instant start;
    public GetTeam opponent;
    public int score;
    public boolean isWinner;
    public int goldEarned;
    public int turretsDestroyed;
    public int inhibitorsDestroyed;
    public GetLolFaction faction;
    public GetLolStructures structures;
    public GetLolCreeps creeps;

    public GetLolTeamMatchStats(GetLolTeamSummary teamSummary, Map<Integer, GetAsset> assetMap) {

        if (teamSummary == null) {
            // Initialize default values
            this.matchId = 0;
            this.score = 0;
            this.isWinner = false;
            this.goldEarned = 0;
            this.turretsDestroyed = 0;
            this.inhibitorsDestroyed = 0;
            this.faction = null;
            this.structures = null;
            this.creeps = null;
            return;
        }

        this.matchId = teamSummary.matchId;
        this.start = teamSummary.start;
        this.opponent = teamSummary.opponent;
        this.score = teamSummary.score;
        this.isWinner = teamSummary.isWinner;
        this.goldEarned = teamSummary.goldEarned;
        this.turretsDestroyed = teamSummary.turretsDestroyed;
        this.inhibitorsDestroyed = teamSummary.inhibitorsDestroyed;
        this.faction = teamSummary.faction;
        this.structures = teamSummary.structures;
        this.creeps = teamSummary.creeps;
    }

    public GetLolTeamMatchStats(LolTeamSummary teamSummary, Map<Integer, GetAsset> assetMap) {

        if (teamSummary == null) {
            // Initialize default values
            this.matchId = 0;
            this.score = 0;
            this.isWinner = false;
            this.goldEarned = 0;
            this.turretsDestroyed = 0;
            this.inhibitorsDestroyed = 0;
            this.faction = null;
            this.structures = null;
            this.creeps = null;
            return;
        }

        this.matchId = teamSummary.getMatchId();
        this.start = Instant.ofEpochMilli(teamSummary.getStart());
        this.opponent = teamSummary.isSetOpponent() ? new GetTeam(teamSummary.getOpponent()) : null;
        this.score = teamSummary.getScore();
        this.isWinner = teamSummary.isIsWinner();
        this.goldEarned = teamSummary.getGoldEarned();
        this.turretsDestroyed = teamSummary.getTurretsDestroyed();
        this.inhibitorsDestroyed = teamSummary.getInhibitorsDestroyed();
        this.faction = teamSummary.isSetFaction() ? new GetLolFaction(teamSummary.getFaction()) : null;
        this.structures = teamSummary.isSetStructures() ? new GetLolStructures(teamSummary.getStructures()) : null;
        this.creeps = teamSummary.isSetCreeps() ? new GetLolCreeps(teamSummary.getCreeps(), assetMap) : null;
    }
}
