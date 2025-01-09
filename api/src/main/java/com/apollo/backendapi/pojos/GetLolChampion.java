package com.apollo.backendapi.pojos;

import java.util.Map;

import com.apollo.backend.data.LolChampion;

public class GetLolChampion {
    public GetAsset champ;

    public GetLolChampion(LolChampion champion, Map<Integer, GetAsset> assetMap) {
        this.champ = assetMap.get(champion.getId());
    }
}