package com.apollo.backendapi.pojos;

import com.apollo.backend.data.LolReviveCount;

public class GetLolReviveCount {
    public int total;

    public GetLolReviveCount(LolReviveCount reviveCount) {
        this.total = reviveCount.getTotal();
    }
}