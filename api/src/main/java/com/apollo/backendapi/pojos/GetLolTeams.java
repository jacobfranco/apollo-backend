package com.apollo.backendapi.pojos;

import com.apollo.backend.data.LolTeams;

public class GetLolTeams {
    public GetLolTeamSummary home;
    public GetLolTeamSummary away;

    public GetLolTeams(LolTeams teams) {
        this.home = new GetLolTeamSummary(teams.getHome());
        this.away = new GetLolTeamSummary(teams.getAway());
    }
}