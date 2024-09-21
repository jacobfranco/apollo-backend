package com.apollo.backendapi.pojos;

import com.apollo.backend.data.LolSummonerSpell;

public class GetLolSummonerSpell {
    public int id;
    public int slot;

    public GetLolSummonerSpell(LolSummonerSpell summonerSpell) {
        this.id = summonerSpell.getId();
        this.slot = summonerSpell.getSlot();
    }
}