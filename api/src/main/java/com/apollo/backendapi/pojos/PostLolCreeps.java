package com.apollo.backendapi.pojos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class PostLolCreeps {
    public PostLolOverallCreeps overall;
    public PostLolNeutralCreeps neutrals;
}