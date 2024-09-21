package com.apollo.backendapi.pojos;

import com.apollo.backend.data.LolPit;

public class GetLolPit {
    public GetLolNpc npc;
    public String npcStatus;
    public GetLolMatchClock emptySinceTime;
    public GetLolMatchClock spawnTime;

    public GetLolPit(LolPit pit) {
        this.npc = new GetLolNpc(pit.getNpc());
        this.npcStatus = pit.getNpcStatus();
        this.emptySinceTime = pit.isSetEmptySinceTime() ? new GetLolMatchClock(pit.getEmptySinceTime()) : null;
        this.spawnTime = pit.isSetSpawnTime() ? new GetLolMatchClock(pit.getSpawnTime()) : null;
    }
}