package com.apollo.backendapi.pojos;

import com.apollo.backend.data.LolInhibitor;

public class GetLolInhibitor {
    public boolean standing;
    public GetLolMatchClock respawnTime;

    public GetLolInhibitor(LolInhibitor inhibitor) {
        this.standing = inhibitor.isStanding();
        this.respawnTime = inhibitor.isSetRespawnTime() ? new GetLolMatchClock(inhibitor.getRespawnTime()) : null;
    }
}