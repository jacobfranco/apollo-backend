package com.apollo.backendapi.pojos;

import com.apollo.backend.data.LolMultiKill;

public class GetLolMultiKill {
    public int nrKills;
    public int count;

    public GetLolMultiKill(LolMultiKill multiKill) {
        this.nrKills = multiKill.getNrKills();
        this.count = multiKill.getCount();
    }
}