package com.apollo.backendapi.pojos;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class PostTournament {
    public int id;
    public String title;
    public String shortTitle;
    public int tier;
    public PostCopy copy;
    public PostLinks links;
    public Instant start;
    public Instant end;
    public PostGame game;
    public PostStringPrizePool stringPrizePool;
    public PostLocation location;
    public Instant deletedAt;
    public List<PostImage> images;
    public List<PostStage> stages;
    public List<PostCaster> casters;
    public List<PostBroadcaster> broadcasters;
    public PostTournamentDefaults defaults;
    public PostCoverage coverage;
    public int resourceVersion;
}
