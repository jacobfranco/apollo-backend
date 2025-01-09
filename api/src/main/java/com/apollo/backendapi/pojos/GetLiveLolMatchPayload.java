package com.apollo.backendapi.pojos;

import java.time.Instant;
import java.util.Map;

import com.apollo.backend.data.LiveLolMatchPayload;

public class GetLiveLolMatchPayload {
    public int index;
    public Instant timestamp;
    public GetLolMatch match;
    public String eventType;
    public GetLolMatchSummary eventData;

    public GetLiveLolMatchPayload(LiveLolMatchPayload thriftPayload, Map<Integer, GetAsset> assetMap) {
        this.index = thriftPayload.getIndex();
        this.timestamp = Instant.parse(thriftPayload.getTimestamp());
        this.match = new GetLolMatch(thriftPayload.getMatch());
        this.eventType = thriftPayload.getEventType();
        this.eventData = new GetLolMatchSummary(thriftPayload.getEventData(), assetMap);
    }
}
