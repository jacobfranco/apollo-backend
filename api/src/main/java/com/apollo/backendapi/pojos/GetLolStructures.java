package com.apollo.backendapi.pojos;

import com.apollo.backend.data.LolStructures;

public class GetLolStructures {
    public GetLolTurrets turrets;
    public GetLolInhibitors inhibitors;

    public GetLolStructures(LolStructures structures) {
        this.turrets = new GetLolTurrets(structures.getTurrets());
        this.inhibitors = new GetLolInhibitors(structures.getInhibitors());
    }
}