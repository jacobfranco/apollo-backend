package com.apollo.backendapi.pojos;

import com.apollo.backend.data.LolChampion;

public class GetLolChampion {
    public int id;

    public GetLolChampion(LolChampion champion) {
        this.id = champion.getId();
    }
}