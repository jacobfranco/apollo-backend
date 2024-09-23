package com.apollo.backendapi.pojos;

import java.util.Map;

import com.apollo.backend.data.LolCreeps;

public class GetLolCreeps {
    public GetLolOverallCreeps overall;
    public GetLolNeutralCreeps neutrals;

    public GetLolCreeps(LolCreeps creeps, Map<Integer, GetAsset> assetMap) {
        this.overall = new GetLolOverallCreeps(creeps.getOverall());
        this.neutrals = new GetLolNeutralCreeps(creeps.getNeutrals(), assetMap);
    }
}