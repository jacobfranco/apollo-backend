package com.apollo.backendapi.pojos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class PostBroadcast {
    @JsonProperty("external_id")
    public String externalId;
    @JsonProperty("language")
    public PostLanguage language;
}