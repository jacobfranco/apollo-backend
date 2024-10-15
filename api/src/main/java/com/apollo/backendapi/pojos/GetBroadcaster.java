package com.apollo.backendapi.pojos;

import com.apollo.backend.data.Broadcaster;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

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
        if (b.getBroadcasts() != null) {
            this.broadcasts = b.getBroadcasts().stream()
                    .map(GetBroadcast::new)
                    .collect(Collectors.toList());
        } else {
            // Decide whether to set it to null or an empty list
            this.broadcasts = Collections.emptyList();
        }
        this.official = b.isOfficial();
    }
}