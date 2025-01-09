package com.apollo.backendapi.pojos;

import com.apollo.backend.data.LolSpecialKills;

public class GetLolSpecialKills {
    public int firstBlood;

    public GetLolSpecialKills(LolSpecialKills specialKills) {
        this.firstBlood = specialKills.getFirstBlood();
    }
}