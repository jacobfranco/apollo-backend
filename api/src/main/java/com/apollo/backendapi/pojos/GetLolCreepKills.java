package com.apollo.backendapi.pojos;

import com.apollo.backend.data.LolCreepKills;

public class GetLolCreepKills {
    public int total;

    public GetLolCreepKills(LolCreepKills creepKills) {
        this.total = creepKills.getTotal();
    }
}