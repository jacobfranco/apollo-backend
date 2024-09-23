package com.apollo.backendapi.pojos;

import java.util.List;
import java.util.Map;

public class GetPlayerMatchStats {
    public int kills;
    public int deaths;
    public int assists;
    public GetAsset champion;
    public List<GetLolItem> items;
    // Other stats...

    // Update the constructor to accept GetLolPlayerSummary
    public GetPlayerMatchStats(GetLolPlayerSummary playerSummary, Map<Integer, GetAsset> assetMap) {
        this.kills = playerSummary.kills.total;
        this.deaths = playerSummary.deaths.total;
        this.assists = playerSummary.assists.total;

        this.champion = playerSummary.champion;
        this.items = playerSummary.items.inventory;
        // Map other stats as needed...
    }
}
