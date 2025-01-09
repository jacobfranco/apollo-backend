package com.apollo.backendapi.pojos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class PostLolTurrets {
    public PostLolTurret top_outer;
    public PostLolTurret top_inner;
    public PostLolTurret top_inhibitor;
    public PostLolTurret top_nexus;
    public PostLolTurret mid_outer;
    public PostLolTurret mid_inner;
    public PostLolTurret mid_inhibitor;
    public PostLolTurret bot_outer;
    public PostLolTurret bot_inner;
    public PostLolTurret bot_inhibitor;
    public PostLolTurret bot_nexus;
}