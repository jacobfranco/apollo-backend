package com.apollo.backendapi.pojos;

import com.apollo.backend.data.LolTurret;

public class GetLolTurret {
    public boolean standing;

    public GetLolTurret(LolTurret turret) {
        this.standing = turret.isStanding();
    }
}