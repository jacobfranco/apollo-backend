package com.apollo.backendapi.pojos;

import com.apollo.backend.data.LolOverallCreeps;

public class GetLolOverallCreeps {
    public GetLolCreepKills kills;

    public GetLolOverallCreeps(LolOverallCreeps overallCreeps) {
        this.kills = new GetLolCreepKills(overallCreeps.getKills());
    }
}