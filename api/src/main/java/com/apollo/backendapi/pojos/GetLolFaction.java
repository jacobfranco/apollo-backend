package com.apollo.backendapi.pojos;

import com.apollo.backend.data.LolFaction;

public class GetLolFaction {
    public int id;

    public GetLolFaction(LolFaction faction) {
        this.id = faction.getId();
    }
}