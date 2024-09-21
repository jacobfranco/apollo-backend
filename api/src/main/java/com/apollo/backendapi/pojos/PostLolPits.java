package com.apollo.backendapi.pojos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class PostLolPits {
    public PostLolPit dragon_pit;
    public PostLolPit baron_pit;
}