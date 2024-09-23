package com.apollo.backendapi.pojos;

import java.time.Instant;
import java.util.Map;

import com.apollo.backend.data.LolMatchSummary;

public class GetLolMatchSummary {
    public int id;
    public GetLolTeams teams;
    public GetLolPits pits;
    public long latestEventsChannelIndex;
    public long latestStatesChannelIndex;
    public Instant timestamp;
    public GetLolMatch match;

    private Map<Integer, GetAsset> assetMap;

    public GetLolMatchSummary(LolMatchSummary summary, Map<Integer, GetAsset> assetMap) {
        this.id = summary.getId();
        this.teams = new GetLolTeams(summary.getTeams(), assetMap);
        this.pits = new GetLolPits(summary.getPits(), assetMap);
        this.latestEventsChannelIndex = summary.getLatestEventsChannelIndex();
        this.latestStatesChannelIndex = summary.getLatestStatesChannelIndex();
        this.timestamp = Instant.parse(summary.getTimestamp());
        this.match = new GetLolMatch(summary.getMatch());
        this.assetMap = assetMap;
    }

    public Map<Integer, GetAsset> getAssetMap() {
        return assetMap;
    }
}
