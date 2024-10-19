package com.apollo.backendapi.pojos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class GetPlayerMatchStats {
    public int kills;
    public int deaths;
    public int assists;
    public int totalCreepScore;
    public int neutralCreepScore;
    public GetLolChampion champion;
    public List<GetLolItem> items;
    public List<GetLolItem> trinketSlot;
    public List<GetLolSummonerSpell> summonerSpells;
    public GetLolKeystone keystone;

    public GetPlayerMatchStats(GetLolPlayerSummary playerSummary, Map<Integer, GetAsset> assetMap) {
        this.kills = playerSummary.kills.total;
        this.deaths = playerSummary.deaths.total;
        this.assists = playerSummary.assists.total;
        this.champion = playerSummary.champion != null ? playerSummary.champion : null;

        if (playerSummary.items != null) {
            this.items = playerSummary.items.inventory != null ? new ArrayList<>(playerSummary.items.inventory)
                    : new ArrayList<>();

            this.trinketSlot = playerSummary.items.trinketSlot != null
                    ? new ArrayList<>(playerSummary.items.trinketSlot)
                    : new ArrayList<>();
        } else {
            this.items = new ArrayList<>();
            this.trinketSlot = new ArrayList<>();
        }

        this.summonerSpells = playerSummary.summonerSpells != null ? new ArrayList<>(playerSummary.summonerSpells)
                : new ArrayList<>();

        // Handle keystone
        this.keystone = playerSummary.keystone != null ? playerSummary.keystone : null;

        if (playerSummary.creeps != null && playerSummary.creeps.overall != null
                && playerSummary.creeps.overall.kills != null) {
            this.totalCreepScore = playerSummary.creeps.overall.kills.total;
        } else {
            this.totalCreepScore = 0;
        }

        // Extract neutral creep score (optional)
        if (playerSummary.creeps != null && playerSummary.creeps.neutrals != null
                && playerSummary.creeps.neutrals.kills != null
                && playerSummary.creeps.neutrals.kills.perEliteType != null) {
            this.neutralCreepScore = playerSummary.creeps.neutrals.kills.perEliteType.stream()
                    .mapToInt(perElite -> perElite.total)
                    .sum();
        } else {
            this.neutralCreepScore = 0;
        }
    }
}
