package com.apollo.backendapi.pojos;

import com.apollo.backend.data.LolDeaths;

public class GetLolDeaths {
    public int total;

    public GetLolDeaths(LolDeaths deaths) {
        this.total = deaths.getTotal();
    }
}