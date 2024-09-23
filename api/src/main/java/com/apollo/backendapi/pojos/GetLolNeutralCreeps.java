package com.apollo.backendapi.pojos;

import java.util.Map;

import com.apollo.backend.data.LolNeutralCreeps;

public class GetLolNeutralCreeps {
    public GetLolNeutralCreepKills kills;

    public GetLolNeutralCreeps(LolNeutralCreeps neutralCreeps, Map<Integer, GetAsset> assetMap) {
        this.kills = new GetLolNeutralCreepKills(neutralCreeps.getKills(), assetMap);
    }
}