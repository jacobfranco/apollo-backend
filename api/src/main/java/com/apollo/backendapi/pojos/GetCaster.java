package com.apollo.backendapi.pojos;

import java.time.Instant;
import com.apollo.backend.data.Caster;

public class GetCaster {
    public int id;
    public String displayName;
    public String username;
    public int gameId;
    public Instant deletedAt;
    public GetStreamingPlatform platform;
    public GetStream stream;
    public GetRegion region;

    public GetCaster(Caster c) {
        this.id = c.getId();
        this.displayName = c.getDisplayName();
        this.username = c.getUsername();
        this.gameId = c.gameId;
        this.deletedAt = c.isSetDeletedAt() ? Instant.ofEpochMilli(c.getDeletedAt()) : null;
        this.platform = c.getPlatform() != null ? new GetStreamingPlatform(c.getPlatform()) : null;
        this.stream = c.getStream() != null ? new GetStream(c.getStream()) : null;
        this.region = c.getRegion() != null ? new GetRegion(c.getRegion()) : null;
    }
}