package com.apollo.backendapi.pojos;

import java.util.Map;

import com.apollo.backend.data.LolPit;

public class GetLolPit {
    public GetAsset npc;
    public String npcStatus;
    public GetLolMatchClock emptySinceTime;
    public GetLolMatchClock spawnTime;

    public GetLolPit(LolPit pit, Map<Integer, GetAsset> assetMap) {
        if (pit.getNpc() != null) {
            this.npc = assetMap.get(pit.getNpc().getId());
        } else {
            this.npc = null; 
        }
        this.npcStatus = pit.getNpcStatus();
        this.emptySinceTime = pit.isSetEmptySinceTime() ? new GetLolMatchClock(pit.getEmptySinceTime()) : null;
        this.spawnTime = pit.isSetSpawnTime() ? new GetLolMatchClock(pit.getSpawnTime()) : null;
    }
}