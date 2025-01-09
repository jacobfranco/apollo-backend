package com.apollo.backendapi.pojos;

import com.apollo.backend.data.LolAssists;

public class GetLolAssists {
    public int total;

    public GetLolAssists(LolAssists assists) {
        this.total = assists.getTotal();
    }
}