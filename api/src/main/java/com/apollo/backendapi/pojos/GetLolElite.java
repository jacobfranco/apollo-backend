package com.apollo.backendapi.pojos;

import com.apollo.backend.data.LolElite;

public class GetLolElite {
    public int id;

    public GetLolElite(LolElite elite) {
        this.id = elite.getId();
    }
}