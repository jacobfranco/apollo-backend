package com.apollo.backendapi.pojos;

import com.apollo.backend.data.ParticipantStats;

public class GetParticipantStats {
    public Integer kills;
    public Integer placement;

    public GetParticipantStats(ParticipantStats stats) {
        this.kills = stats.isSetKills() ? stats.getKills() : null;
        this.placement = stats.isSetPlacement() ? stats.getPlacement() : null;
    }
}