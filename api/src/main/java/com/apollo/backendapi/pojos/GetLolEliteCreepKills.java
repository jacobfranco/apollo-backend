package com.apollo.backendapi.pojos;

import java.util.Map;

import com.apollo.backend.data.LolEliteCreepKills;

public class GetLolEliteCreepKills {
    public GetAsset elite;
    public int total;

    public GetLolEliteCreepKills(LolEliteCreepKills eliteCreepKills, Map<Integer, GetAsset> assetMap) {
        this.elite = assetMap.get(eliteCreepKills.getElite().getId());
        this.total = eliteCreepKills.getTotal();
    }
}