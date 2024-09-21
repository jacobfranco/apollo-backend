package com.apollo.backendapi.pojos;

import com.apollo.backend.data.LolNeutralCreeps;

public class GetLolNeutralCreeps {
    public GetLolNeutralCreepKills kills;

    public GetLolNeutralCreeps(LolNeutralCreeps neutralCreeps) {
        this.kills = new GetLolNeutralCreepKills(neutralCreeps.getKills());
    }
}