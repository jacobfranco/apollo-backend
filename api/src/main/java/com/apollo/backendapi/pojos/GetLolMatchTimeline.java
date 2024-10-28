package com.apollo.backendapi.pojos;

import java.time.Instant;
import com.apollo.backend.data.LolMatchTimeline;

public class GetLolMatchTimeline {
    public String phase;
    public Instant start;
    public Instant end;
    public GetLolMatchClock clock;

    public GetLolMatchTimeline(LolMatchTimeline timeline) {
        if (timeline == null) {
            return;
        }
        
        this.phase = timeline.getPhase();
        
        // Add null checks for start and end times
        String startTime = timeline.getStart();
        if (startTime != null) {
            this.start = Instant.parse(startTime);
        }
        
        String endTime = timeline.getEnd();
        if (endTime != null) {
            this.end = Instant.parse(endTime);
        }
        
        // Add null check for clock
        if (timeline.getClock() != null) {
            this.clock = new GetLolMatchClock(timeline.getClock());
        }
    }
}