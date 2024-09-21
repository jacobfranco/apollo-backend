package com.apollo.backendapi.pojos;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class PostLolNeutralCreepKills {
    public List<PostLolEliteCreepKills> per_elite_type;
}
