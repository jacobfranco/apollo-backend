package com.apollo.backendapi.pojos;

import com.apollo.backend.data.StandingRoster;
import java.time.Instant;

public class GetStandingRoster {
    public int id;
    public Instant from;
    public Instant to;
    public int rosterId;
    public Instant deletedAt;

    public GetStandingRoster(StandingRoster standingRoster) {
        this.id = standingRoster.getId();
        this.from = Instant.ofEpochMilli(standingRoster.getFrom());
        this.to = standingRoster.isSetTo() ? Instant.ofEpochMilli(standingRoster.getTo()) : null;
        this.rosterId = standingRoster.getRosterId();
        this.deletedAt = standingRoster.isSetDeletedAt() ? Instant.ofEpochMilli(standingRoster.getDeletedAt()) : null;
    }
}