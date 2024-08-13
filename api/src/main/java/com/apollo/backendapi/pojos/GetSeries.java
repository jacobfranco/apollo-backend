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
    public BracketPosition bracketPosition;
    public List<Participant> participants;
    public int tournamentId;
    public int substageId;
    public int gameId;
    public List<Integer> matchIds;
    public List<Caster> casters;
    public List<Broadcaster> broadcasters;
    public boolean hasIncidentReport;
    public Coverage coverage;
    public Format format;
    public GameVersion gameVersion;
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
        this.bracketPosition = series.isSetBracketPosition() ? new BracketPosition(series.getBracketPosition()) : null;
        this.participants = series.getParticipants().stream().map(Participant::new).toList();
        this.tournamentId = series.getTournamentId();
        this.substageId = series.getSubstageId();
        this.gameId = series.getGameId();
        this.matchIds = series.getMatchIds();
        this.casters = series.getCasters().stream().map(Caster::new).toList();
        this.broadcasters = series.getBroadcasters().stream().map(Broadcaster::new).toList();
        this.hasIncidentReport = series.isHasIncidentReport();
        this.coverage = series.isSetCoverage() ? new Coverage(series.getCoverage()) : null;
        this.format = series.isSetFormatBestOf() ? new Format(series.getFormatBestOf()) : null;
        this.gameVersion = series.isSetGameVersion() ? new GameVersion(series.getGameVersion()) : null;
        this.resourceVersion = series.getResourceVersion();
        this.createdAt = Instant.ofEpochMilli(series.getCreatedAt());
        this.updatedAt = Instant.ofEpochMilli(series.getUpdatedAt());
    }

    // Inner classes corresponding to the structure of Series
    public static class BracketPosition {
        public String part;
        public int col;
        public int offset;

        public BracketPosition(com.apollo.backend.data.BracketPosition bp) {
            this.part = bp.getPart();
            this.col = bp.getCol();
            this.offset = bp.getOffset();
        }
    }

    public static class Participant {
        public int seed;
        public int score;
        public boolean forfeit;
        public int rosterId;
        public boolean winner;
        public ParticipantStats stats;

        public Participant(com.apollo.backend.data.Participant p) {
            this.seed = p.getSeed();
            this.score = p.getScore();
            this.forfeit = p.isForfeit();
            this.rosterId = p.getRosterId();
            this.winner = p.isWinner();
            this.stats = p.isSetStats() ? new ParticipantStats(p.getStats()) : null;
        }
    }

    public static class ParticipantStats {
        public Integer kills;
        public Integer placement;

        public ParticipantStats(com.apollo.backend.data.ParticipantStats stats) {
            this.kills = stats.isSetKills() ? stats.getKills() : null;
            this.placement = stats.isSetPlacement() ? stats.getPlacement() : null;
        }
    }

    public static class Caster {
        public boolean primary;
        public int casterId;

        public Caster(com.apollo.backend.data.Caster c) {
            this.primary = c.isPrimary();
            this.casterId = c.getCasterId();
        }
    }

    public static class Broadcaster {
        public int broadcasterId;
        public String broadcasterName;
        public String broadcasterExternalId;
        public int broadcasterPlatformId;
        public int broadcasterDefaultLanguageId;
        public List<Broadcast> broadcasts;
        public boolean official;

        public Broadcaster(com.apollo.backend.data.Broadcaster b) {
            this.broadcasterId = b.getBroadcasterId();
            this.broadcasterName = b.getBroadcasterName();
            this.broadcasterExternalId = b.getBroadcasterExternalId();
            this.broadcasterPlatformId = b.getBroadcasterPlatformId();
            this.broadcasterDefaultLanguageId = b.getBroadcasterDefaultLanguageId();
            this.broadcasts = b.getBroadcasts().stream().map(Broadcast::new).toList();
            this.official = b.isOfficial();
        }
    }

    public static class Broadcast {
        public String externalId;
        public int languageId;

        public Broadcast(com.apollo.backend.data.Broadcast b) {
            this.externalId = b.getExternalId();
            this.languageId = b.getLanguageId();
        }
    }

    public static class GameVersion {
        public Release release;

        public GameVersion(com.apollo.backend.data.GameVersion gv) {
            this.release = gv.isSetRelease() ? new Release(gv.getRelease()) : null;
        }
    }

    public static class Release {
        public String uuid;
        public String date;
        public String description;

        public Release(com.apollo.backend.data.Release r) {
            this.uuid = r.getUuid();
            this.date = r.getDate();
            this.description = r.getDescription();
        }
    }

    public static class Coverage {
        public CoverageData data;

        public Coverage(com.apollo.backend.data.Coverage c) {
            this.data = c.isSetData() ? new CoverageData(c.getData()) : null;
        }
    }

    public static class CoverageData {
        public CoverageType live;
        public CoverageType realtime;
        public CoverageType postgame;

        public CoverageData(com.apollo.backend.data.CoverageData cd) {
            this.live = cd.isSetLive() ? new CoverageType(cd.getLive()) : null;
            this.realtime = cd.isSetRealtime() ? new CoverageType(cd.getRealtime()) : null;
            this.postgame = cd.isSetPostgame() ? new CoverageType(cd.getPostgame()) : null;
        }
    }

    public static class CoverageType {
        public CoverageStatus api;
        public CoverageStatus cv;
        public CoverageStatus server;

        public CoverageType(com.apollo.backend.data.CoverageType ct) {
            this.api = ct.isSetApi() ? new CoverageStatus(ct.getApi()) : null;
            this.cv = ct.isSetCv() ? new CoverageStatus(ct.getCv()) : null;
            this.server = ct.isSetServer() ? new CoverageStatus(ct.getServer()) : null;
        }
    }

    public static class CoverageStatus {
        public String expectation;
        public String fact;

        public CoverageStatus(com.apollo.backend.data.CoverageStatus cs) {
            this.expectation = cs.getExpectation();
            this.fact = cs.getFact();
        }
    }

    public static class Format {
        public int bestOf;

        public Format(int bestOf) {
            this.bestOf = bestOf;
        }
    }
}
