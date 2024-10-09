package com.apollo.backendapi.pojos;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.List;
import java.util.Collections;

@JsonIgnoreProperties(ignoreUnknown = true)
public class PostMatch {
    public int id;
    public PostMapInfo map;
    public String lifecycle;
    public int order;
    public PostSeriesInfo series;

    public Instant deletedAt;

    public PostGameInfo game;
    public List<PostParticipant> participants = Collections.emptyList();
    public PostCoverage coverage;
    public long resourceVersion;

    // Getter methods to extract flat fields
    public int getMapId() {
        return map != null ? map.id : 0;
    }

    public int getSeriesId() {
        return series != null ? series.id : 0;
    }

    public int getGameId() {
        return game != null ? game.id : 0;
    }
}
