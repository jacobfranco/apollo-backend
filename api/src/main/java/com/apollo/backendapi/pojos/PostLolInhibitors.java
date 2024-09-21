package com.apollo.backendapi.pojos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class PostLolInhibitors {
    public PostLolInhibitor top;
    public PostLolInhibitor mid;
    public PostLolInhibitor bot;
}