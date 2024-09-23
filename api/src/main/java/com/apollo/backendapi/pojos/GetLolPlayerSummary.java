package com.apollo.backendapi.pojos;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import com.apollo.backend.data.LolPlayerSummary;

public class GetLolPlayerSummary {
    public int id;
    public int uiIndex;
    public GetAsset champion;
    public GetLolKills kills;
    public GetLolAssists assists;
    public GetLolDeaths deaths;
    public GetLolRevives revives;
    public List<GetLolMultiKill> multiKills;
    public List<Integer> killStreaks;
    public GetLolItems items;
    public List<GetAsset> summonerSpells;
    public GetLolCreeps creeps;
    public GetAsset keystone;
    public GetLolPosition position;

    public GetLolPlayerSummary(LolPlayerSummary playerSummary, Map<Integer, GetAsset> assetMap) {
        this.id = playerSummary.getId();
        this.uiIndex = playerSummary.getUiIndex();
        this.champion = assetMap.get(playerSummary.getChampion().getId());
        this.kills = new GetLolKills(playerSummary.getKills());
        this.assists = new GetLolAssists(playerSummary.getAssists());
        this.deaths = new GetLolDeaths(playerSummary.getDeaths());
        this.revives = new GetLolRevives(playerSummary.getRevives());
        this.multiKills = Optional.ofNullable(playerSummary.getMultiKills())
                .orElse(Collections.emptyList())
                .stream()
                .map(GetLolMultiKill::new)
                .collect(Collectors.toList());
        this.killStreaks = playerSummary.getKillStreaks();
        this.items = new GetLolItems(playerSummary.getItems(), assetMap);
        this.summonerSpells = playerSummary.getSummonerSpells().stream()
                .map(spell -> assetMap.get(spell.getId()))
                .collect(Collectors.toList());
        this.creeps = new GetLolCreeps(playerSummary.getCreeps(), assetMap);
        this.keystone = playerSummary.isSetKeystone() ? assetMap.get(playerSummary.getKeystone().getId()) : null;
        this.position = playerSummary.isSetPosition() ? new GetLolPosition(playerSummary.getPosition()) : null;
    }
}