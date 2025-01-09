package com.apollo.backendapi.pojos;

import com.apollo.backend.data.LolMatchClock;

public class GetLolMatchClock {
    public int milliseconds;

    public GetLolMatchClock(LolMatchClock clock) {
        this.milliseconds = clock.getMilliseconds();
    }
}