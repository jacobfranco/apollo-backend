package com.apollo.backendapi.pojos;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

import com.apollo.backend.data.Tournament;

public class GetTournament {
    public int id;
    public String title;
    public String shortTitle;
    public int tier;
    public GetTournamentCopy copy;
    public GetTournamentLinks links;
    public Instant start;
    public Instant end;
    public int gameId;
    public GetStringPrizePool stringPrizePool;
    public GetTournamentLocation location;
    public Instant deletedAt;
    public List<GetImage> images;
    public List<Integer> stageIds;
    public List<GetCaster> casters;
    public List<GetBroadcaster> broadcasters;
    public GetTournamentDefaults defaults;
    public GetCoverage coverage;
    public int resourceVersion;

    public GetTournament(Tournament t) {
        this.id = t.getId();
        this.title = t.getTitle();
        this.shortTitle = t.getShortTitle();
        this.tier = t.getTier();
        this.copy = t.isSetCopy() ? new GetTournamentCopy(t.getCopy()) : null;
        this.links = t.isSetLinks() ? new GetTournamentLinks(t.getLinks()) : null;
        this.start = Instant.ofEpochMilli(t.getStart());
        this.end = Instant.ofEpochMilli(t.getEnd());
        this.gameId = t.getGameId();
        this.stringPrizePool = t.isSetStringPrizePool() ? new GetStringPrizePool(t.getStringPrizePool()) : null;
        this.location = t.isSetLocation() ? new GetTournamentLocation(t.getLocation()) : null;
        this.deletedAt = t.isSetDeletedAt() ? Instant.ofEpochMilli(t.getDeletedAt()) : null;
        this.images = t.getImages().stream().map(GetImage::new).collect(Collectors.toList());
        this.stageIds = t.getStageIds();
        this.casters = t.getCasters().stream().map(GetCaster::new).collect(Collectors.toList());
        this.broadcasters = t.getBroadcasters().stream().map(GetBroadcaster::new).collect(Collectors.toList());
        this.defaults = t.isSetDefaults() ? new GetTournamentDefaults(t.getDefaults()) : null;
        this.coverage = t.isSetCoverage() ? new GetCoverage(t.getCoverage()) : null;
        this.resourceVersion = t.getResourceVersion();
    }
}
