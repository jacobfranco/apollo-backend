package com.apollo.backendapi.pojos;

import java.time.Instant;
import java.util.Map;

import com.apollo.backend.data.LiveLolMatchSummary;

public class GetLiveLolMatchSummary {
    public String channel;
    public String uuid;
    public Instant created;
    public GetLiveLolMatchPayload payload;

    public GetLiveLolMatchSummary(LiveLolMatchSummary thriftSummary, Map<Integer, GetAsset> assetMap) {
        this.channel = thriftSummary.getChannel();
        this.uuid = thriftSummary.getUuid();
        this.created = Instant.ofEpochMilli(thriftSummary.getCreated());
        this.payload = new GetLiveLolMatchPayload(thriftSummary.getPayload(), assetMap);
    }
}
