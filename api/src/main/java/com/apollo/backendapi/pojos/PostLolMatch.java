package com.apollo.backendapi.pojos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class PostLolMatch {
    public int id;
    public String patch;
    public String phase;
    public PostLolMatchClock clock;
    public PostLolMatchTimeline timeline;
}
