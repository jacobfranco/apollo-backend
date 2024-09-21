package com.apollo.backendapi.pojos;

import com.apollo.backend.data.LolEliteCreepKills;

public class GetLolEliteCreepKills {
    public GetLolElite elite;
    public int total;

    public GetLolEliteCreepKills(LolEliteCreepKills eliteCreepKills) {
        this.elite = new GetLolElite(eliteCreepKills.getElite());
        this.total = eliteCreepKills.getTotal();
    }
}