package com.apollo.backendapi.pojos;

import com.apollo.backend.data.LolNpc;

public class GetLolNpc {
    public int id;

    public GetLolNpc(LolNpc npc) {
        this.id = npc.getId();
    }
}