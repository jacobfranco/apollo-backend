package com.apollo.backendapi.pojos;

import java.util.Map;

import com.apollo.backend.data.LolSummonerSpell;

public class GetLolSummonerSpell {
    public GetAsset spell;
    public int slot;

    public GetLolSummonerSpell(LolSummonerSpell summonerSpell, Map<Integer, GetAsset> assetMap) {
        this.spell = assetMap.get(summonerSpell.getId());
        this.slot = summonerSpell.getSlot();
    }
}