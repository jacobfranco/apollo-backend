package com.apollo.backendapi.pojos;

import com.apollo.backend.data.LolPits;

public class GetLolPits {
    public GetLolPit dragonPit;
    public GetLolPit baronPit;

    public GetLolPits(LolPits pits) {
        this.dragonPit = new GetLolPit(pits.getDragonPit());
        this.baronPit = new GetLolPit(pits.getBaronPit());
    }
}