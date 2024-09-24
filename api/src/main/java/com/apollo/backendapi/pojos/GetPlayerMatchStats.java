package com.apollo.backendapi.pojos;

import java.util.List;
import java.util.Map;

public class GetPlayerMatchStats {
    public int kills;
    public int deaths;
    public int assists;
    public int totalCreepScore;
    public int neutralCreepScore;
    public GetAsset champion;
    public List<GetLolItem> items;

    public GetPlayerMatchStats(GetLolPlayerSummary playerSummary, Map<Integer, GetAsset> assetMap) {
        this.kills = playerSummary.kills.total;
        this.deaths = playerSummary.deaths.total;
        this.assists = playerSummary.assists.total;

        this.champion = playerSummary.champion;
        this.items = playerSummary.items.inventory;
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
