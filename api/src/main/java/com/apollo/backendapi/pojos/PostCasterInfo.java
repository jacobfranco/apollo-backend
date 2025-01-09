package com.apollo.backendapi.pojos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class PostCasterInfo {
    public boolean primary;
    public CasterId caster;

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CasterId {
        public Integer id;
    }
}
