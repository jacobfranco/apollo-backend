package com.apollo.backendapi.pojos;

import com.apollo.backend.data.LolFaction;

public class GetLolFaction {
    public String factionName;

    public GetLolFaction(LolFaction faction) {
        this.factionName = mapFactionIdToName(faction.getId());
    }

    private String mapFactionIdToName(int id) {
        switch (id) {
            case 5444:
                return "red";
            case 5445:
                return "blue";
            default:
                return "unknown";
        }
    }
}
