package com.apollo.backendapi.pojos;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import com.apollo.backend.data.LolPlayerSummary;

public class GetLolPlayerSummary {
    public int id;
    public int uiIndex;
    public GetLolChampion champion;
    public GetLolKills kills;
    public GetLolAssists assists;
    public GetLolDeaths deaths;
    public GetLolRevives revives;
    public List<GetLolMultiKill> multiKills;
    public List<Integer> killStreaks;
    public GetLolItems items;
    public List<GetLolSummonerSpell> summonerSpells;
    public GetLolCreeps creeps;
    public GetLolKeystone keystone;
    public GetLolPosition position;

    public GetLolPlayerSummary(LolPlayerSummary playerSummary) {
        this.id = playerSummary.getId();
        this.uiIndex = playerSummary.getUiIndex();
        this.champion = new GetLolChampion(playerSummary.getChampion());
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
        this.items = new GetLolItems(playerSummary.getItems());
        this.summonerSpells = playerSummary.getSummonerSpells().stream()
                .map(GetLolSummonerSpell::new)
                .collect(Collectors.toList());
        this.creeps = new GetLolCreeps(playerSummary.getCreeps());
        this.keystone = playerSummary.isSetKeystone() ? new GetLolKeystone(playerSummary.getKeystone()) : null;
        this.position = playerSummary.isSetPosition() ? new GetLolPosition(playerSummary.getPosition()) : null;
    }
}