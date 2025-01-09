package com.apollo.backendapi.pojos;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class PostSubstage {
    public Integer id;
    public PostStageInfo stage;
    public String title;
    public int tier;
    public int type;
    public String phase;
    public PostFormat defaultSeriesFormat;
    public PostGame game;
    public PostTournamentInfo tournament;
    public int order;
    public List<PostRosterInfo> rosters;
    public Instant start;
    public Instant deletedAt;
    public List<PostStanding> standings;
    public PostRules rules;
    public PostSubstageDefaults defaults;
    public PostSubstageFormat format;
    public PostCoverage coverage;
    public int resourceVersion;
}