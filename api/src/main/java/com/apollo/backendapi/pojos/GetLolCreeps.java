package com.apollo.backendapi.pojos;

import com.apollo.backend.data.LolCreeps;

public class GetLolCreeps {
    public GetLolOverallCreeps overall;
    public GetLolNeutralCreeps neutrals;

    public GetLolCreeps(LolCreeps creeps) {
        this.overall = new GetLolOverallCreeps(creeps.getOverall());
        this.neutrals = new GetLolNeutralCreeps(creeps.getNeutrals());
    }
}