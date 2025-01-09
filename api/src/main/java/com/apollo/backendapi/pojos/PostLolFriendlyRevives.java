package com.apollo.backendapi.pojos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class PostLolFriendlyRevives {
    public PostLolReviveCount given;
    public PostLolReviveCount taken;
}
