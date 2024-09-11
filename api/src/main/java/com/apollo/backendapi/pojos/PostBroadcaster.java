package com.apollo.backendapi.pojos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class PostBroadcaster {
    @JsonProperty("broadcaster.id")
    public int broadcasterId;
    @JsonProperty("broadcaster.name")
    public String broadcasterName;
    @JsonProperty("broadcaster.external_id")
    public String broadcasterExternalId;
    @JsonProperty("broadcaster.platform.id")
    public int broadcasterPlatformId;
    @JsonProperty("broadcaster.broadcast_defaults.language.id")
    public int broadcasterDefaultLanguageId;
    public List<PostBroadcast> broadcasts;
    public boolean official;
}