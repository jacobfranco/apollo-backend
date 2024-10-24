package com.apollo.backendapi.pojos;

import com.apollo.backend.data.LolMatch;

public class GetLolMatch {
    public int id;
    public String patch;
    public String phase;
    public GetLolMatchClock clock;
    public GetLolMatchTimeline timeline;

    public GetLolMatch(LolMatch match) {
        this.id = match.getId();
        this.patch = match.getPatch();
        this.phase = match.getPhase();
        this.clock = new GetLolMatchClock(match.getClock());
        this.timeline = new GetLolMatchTimeline(match.getTimeline());
    }

    public GetLolMatch() {
        this.id = -1; // Default or sentinel value
        // Initialize other fields with defaults
    }
}