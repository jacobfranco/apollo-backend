package com.apollo.backendapi.pojos;

import com.apollo.backend.data.Series;
import java.time.Instant;
import java.util.List;

public class GetSeries {
    public int id;
    public String title;
    public Instant start;
    public Instant end;
    public Instant postponedFrom;
    public Instant deletedAt;
    public String lifecycle;
    public int tier;
    public int bestOf;
    public List<Integer> chainIds;
    public boolean streamed;
    public GetBracketPosition bracketPosition;
    public List<GetParticipant> participants;
    public int tournamentId;
    public int substageId;
    public int gameId;
    public List<Integer> matchIds;
    public List<GetCaster> casters;
    public List<GetBroadcaster> broadcasters;
    public boolean hasIncidentReport;
    public GetCoverage coverage;
    public GetFormat format;
    public GetGameVersion gameVersion;
    public long resourceVersion;
    public Instant createdAt;
    public Instant updatedAt;

    // Constructor to convert Series (Thrift) to GetSeries
    public GetSeries(Series series) {
        this.id = series.getId();
        this.title = series.getTitle();
        this.start = Instant.ofEpochMilli(series.getStart());
        this.end = Instant.ofEpochMilli(series.getEnd());
        this.postponedFrom = series.isSetPostponedFrom() ? Instant.ofEpochMilli(series.getPostponedFrom()) : null;
        this.deletedAt = series.isSetDeletedAt() ? Instant.ofEpochMilli(series.getDeletedAt()) : null;
        this.lifecycle = series.getLifecycle();
        this.tier = series.getTier();
        this.bestOf = series.getBestOf();
        this.chainIds = series.getChainIds();
        this.streamed = series.isStreamed();
        this.bracketPosition = series.isSetBracketPosition() ? new GetBracketPosition(series.getBracketPosition())
                : null;
        this.participants = series.getParticipants().stream().map(GetParticipant::new).toList();
        this.tournamentId = series.getTournamentId();
        this.substageId = series.getSubstageId();
        this.gameId = series.getGameId();
        this.matchIds = series.getMatchIds();
        this.casters = series.getCasters().stream().map(GetCaster::new).toList();
        this.broadcasters = series.getBroadcasters().stream().map(GetBroadcaster::new).toList();
        this.hasIncidentReport = series.isHasIncidentReport();
        this.coverage = series.isSetCoverage() ? new GetCoverage(series.getCoverage()) : null;
        this.format = series.isSetFormatBestOf() ? new GetFormat(series.getFormatBestOf()) : null;
        this.gameVersion = series.isSetGameVersion() ? new GetGameVersion(series.getGameVersion()) : null;
        this.resourceVersion = series.getResourceVersion();
        this.createdAt = Instant.ofEpochMilli(series.getCreatedAt());
        this.updatedAt = Instant.ofEpochMilli(series.getUpdatedAt());
    }
}