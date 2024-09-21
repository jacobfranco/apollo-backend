package com.apollo.backendapi.pojos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class PostLolPosition {
    public double x;
    public double y;
    public PostLolNormalizedCoordinate normalized_coordinate;
    public PostLolInGameCoordinate in_game_coordinate;
}
