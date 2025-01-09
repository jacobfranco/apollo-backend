package com.apollo.backendapi.pojos;

import com.apollo.backend.data.Standing;

public class GetStanding {
    public int rosterId;
    public int points;
    public int wins;
    public int draws;
    public int losses;
    public int matchDiff;
    public int scoreDiff;

    public GetStanding(Standing standing) {
        this.rosterId = standing.getRosterId();
        this.points = standing.getPoints();
        this.wins = standing.getWins();
        this.draws = standing.getDraws();
        this.losses = standing.getLosses();
        this.matchDiff = standing.getMatchDiff();
        this.scoreDiff = standing.getScoreDiff();
    }
}
