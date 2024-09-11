package com.apollo.backendapi.pojos;

import com.apollo.backend.data.Broadcaster;

import java.util.List;

public class GetBroadcaster {
    public int broadcasterId;
    public String broadcasterName;
    public String broadcasterExternalId;
    public int broadcasterPlatformId;
    public int broadcasterDefaultLanguageId;
    public List<GetBroadcast> broadcasts;
    public boolean official;

    public GetBroadcaster(Broadcaster b) {
        this.broadcasterId = b.getBroadcasterId();
        this.broadcasterName = b.getBroadcasterName();
        this.broadcasterExternalId = b.getBroadcasterExternalId();
        this.broadcasterPlatformId = b.getBroadcasterPlatformId();
        this.broadcasterDefaultLanguageId = b.getBroadcasterDefaultLanguageId();
        this.broadcasts = b.getBroadcasts().stream().map(GetBroadcast::new).toList();
        this.official = b.isOfficial();
    }
}