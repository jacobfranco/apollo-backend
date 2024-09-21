package com.apollo.backendapi.pojos;

import com.apollo.backend.data.LolKills;

public class GetLolKills {
    public int total;
    public GetLolSpecialKills special;

    public GetLolKills(LolKills kills) {
        this.total = kills.getTotal();
        this.special = new GetLolSpecialKills(kills.getSpecial());
    }
}