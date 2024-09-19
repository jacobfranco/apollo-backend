package com.apollo.backendapi.pojos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class PostSocialMediaAccount {
    public String handle;
    public String url;
    public PostPlatform platform;
}