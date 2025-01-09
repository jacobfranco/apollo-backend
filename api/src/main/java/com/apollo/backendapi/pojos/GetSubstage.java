package com.apollo.backendapi.pojos;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

import com.apollo.backend.data.Substage;

public class GetSubstage {
    public int id;
    public int stageId;
    public String title;
    public int tier;
    public int type;
    public String phase;
    public GetFormat defaultSeriesFormat;
    public int gameId;
    public int tournamentId;
    public int order;
    public List<Integer> rosterIds;
    public Instant start;
    public Instant deletedAt;
    public List<GetStanding> standings;
    public GetSubstageRules rules;
    public GetSubstageDefaults defaults;
    public GetSubstageFormat format;
    public GetCoverage coverage;
    public int resourceVersion;

    public GetSubstage(Substage s) {
        this.id = s.getId();
        this.stageId = s.getStageId();
        this.title = s.getTitle();
        this.tier = s.getTier();
        this.type = s.getType();
        this.phase = s.getPhase();
        this.defaultSeriesFormat = s.isSetDefaultSeriesFormat() ? new GetFormat(s.getDefaultSeriesFormat()) : null;
        this.gameId = s.getGameId();
        this.tournamentId = s.getTournamentId();
        this.order = s.getOrder();
        this.rosterIds = s.getRosterIds();
        this.start = Instant.ofEpochMilli(s.getStart());
        this.deletedAt = s.isSetDeletedAt() ? Instant.ofEpochMilli(s.getDeletedAt()) : null;
        this.standings = s.getStandings().stream().map(GetStanding::new).collect(Collectors.toList());
        this.rules = s.isSetRules() ? new GetSubstageRules(s.getRules()) : null;
        this.defaults = s.isSetDefaults() ? new GetSubstageDefaults(s.getDefaults()) : null;
        this.format = s.isSetFormat() ? new GetSubstageFormat(s.getFormat()) : null;
        this.coverage = s.isSetCoverage() ? new GetCoverage(s.getCoverage()) : null;
        this.resourceVersion = s.getResourceVersion();
    }
}
