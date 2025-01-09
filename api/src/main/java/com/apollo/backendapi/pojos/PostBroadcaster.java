package com.apollo.backendapi.pojos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class PostBroadcaster {
    public BroadcasterDetails broadcaster;
    public List<PostBroadcast> broadcasts;
    public boolean official;

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class BroadcasterDetails {
        public int id;
        public String name;
        public String external_id;
        public PostStreamingPlatform platform;
        public PostBroadcastDefaults broadcast_defaults;
    }
}
