package com.apollo.backendapi.pojos;

import java.time.Instant;
import com.apollo.backend.data.LolMatchSummary;

public class GetLolMatchSummary {
    public int id;
    public GetLolTeams teams;
    public GetLolPits pits;
    public long latestEventsChannelIndex;
    public long latestStatesChannelIndex;
    public Instant timestamp;
    public GetLolMatch match;

    public GetLolMatchSummary(LolMatchSummary summary) {
        this.id = summary.getId();
        this.teams = new GetLolTeams(summary.getTeams());
        this.pits = new GetLolPits(summary.getPits());
        this.latestEventsChannelIndex = summary.getLatestEventsChannelIndex();
        this.latestStatesChannelIndex = summary.getLatestStatesChannelIndex();
        this.timestamp = Instant.parse(summary.getTimestamp());
        this.match = new GetLolMatch(summary.getMatch());
    }
}