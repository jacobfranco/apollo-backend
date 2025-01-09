package com.apollo.backendapi.pojos;

import java.util.Map;

import com.apollo.backend.data.LolPits;

public class GetLolPits {
    public GetLolPit dragonPit;
    public GetLolPit baronPit;

    public GetLolPits(LolPits pits, Map<Integer, GetAsset> assetMap) {
        this.dragonPit = new GetLolPit(pits.getDragonPit(), assetMap);
        this.baronPit = new GetLolPit(pits.getBaronPit(), assetMap);
    }
}