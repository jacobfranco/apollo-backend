package com.apollo.backendapi.pojos;

import java.time.Instant;
import com.apollo.backend.data.LolMatchTimeline;

public class GetLolMatchTimeline {
    public String phase;
    public Instant start;
    public Instant end;
    public GetLolMatchClock clock;

    public GetLolMatchTimeline(LolMatchTimeline timeline) {
        this.phase = timeline.getPhase();
        this.start = Instant.parse(timeline.getStart());
        this.end = Instant.parse(timeline.getEnd());
        this.clock = new GetLolMatchClock(timeline.getClock());
    }
}