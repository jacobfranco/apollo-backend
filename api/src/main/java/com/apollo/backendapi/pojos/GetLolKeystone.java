package com.apollo.backendapi.pojos;

import com.apollo.backend.data.LolKeystone;

public class GetLolKeystone {
    public int id;

    public GetLolKeystone(LolKeystone keystone) {
        this.id = keystone.getId();
    }
}