package com.apollo.backendapi.pojos;

import java.util.Map;

import com.apollo.backend.data.LolTeams;

public class GetLolTeams {
    public GetLolTeamSummary home;
    public GetLolTeamSummary away;

    public GetLolTeams(LolTeams teams, Map<Integer, GetAsset> assetMap) {
        this.home = new GetLolTeamSummary(teams.getHome(), assetMap);
        this.away = new GetLolTeamSummary(teams.getAway(), assetMap);
    }
}