package com.apollo.backendapi.pojos;

import com.apollo.backend.data.Match;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

public class GetMatch {
    public int id;
    public GetMapInfo map;
    public String lifecycle;
    public int order;
    public GetSeriesInfo series;
    public Instant deletedAt;
    public GetGameInfo game;
    public List<GetParticipant> participants;
    public GetCoverage coverage;
    public long resourceVersion;

    public GetMatch(Match match) {
        this.id = match.getId();
        this.map = new GetMapInfo(match.getMapId());
        this.lifecycle = match.getLifecycle();
        this.order = match.getOrder();
        this.series = new GetSeriesInfo(match.getSeriesId());
        this.deletedAt = match.isSetDeletedAt() ? Instant.ofEpochMilli(match.getDeletedAt()) : null;
        this.game = new GetGameInfo(match.getGameId());
        this.participants = match.getParticipants().stream()
                .map(GetParticipant::new)
                .collect(Collectors.toList());
        this.coverage = match.isSetCoverage() ? new GetCoverage(match.getCoverage()) : null;
        this.resourceVersion = match.getResourceVersion();
    }
}
