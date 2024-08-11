package com.apollo.backendapi.pojos;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class PostSeries {
    public int id;
    public String title;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
    public Instant start;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
    public Instant end;

    @JsonProperty("postponed_from")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
    public Instant postponedFrom;

    @JsonProperty("deleted_at")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
    public Instant deletedAt;

    public String lifecycle;
    public int tier;
    @JsonProperty("best_of")
    public int bestOf;
    @JsonProperty("chain[*].id")
    public List<Integer> chainIds;
    public boolean streamed;
    @JsonProperty("bracket_position")
    public BracketPosition bracketPosition;
    public List<Participant> participants;
    @JsonProperty("tournament.id")
    public int tournamentId;
    @JsonProperty("substage.id")
    public int substageId;
    @JsonProperty("game.id")
    public int gameId;
    @JsonProperty("matches[*].id")
    public List<Integer> matchIds;
    public List<Caster> casters;
    public List<Broadcaster> broadcasters;
    @JsonProperty("has_incident_report")
    public boolean hasIncidentReport;
    public Coverage coverage;
    public Format format;
    @JsonProperty("game_version")
    public GameVersion gameVersion;
    @JsonProperty("resource_version")
    public long resourceVersion;

    @JsonProperty("created_at")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
    public Instant createdAt;

    @JsonProperty("updated_at")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
    public Instant updatedAt;

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class BracketPosition {
        public String part;
        public int col;
        public int offset;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Participant {
        public int seed;
        public int score;
        public boolean forfeit;
        @JsonProperty("roster.id")
        public int rosterId;
        public boolean winner;
        public ParticipantStats stats;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ParticipantStats {
        public Integer kills;
        public Integer placement;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Caster {
        public boolean primary;
        @JsonProperty("caster.id")
        public int casterId;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Broadcaster {
        @JsonProperty("broadcaster.id")
        public int broadcasterId;
        @JsonProperty("broadcaster.name")
        public String broadcasterName;
        @JsonProperty("broadcaster.external_id")
        public String broadcasterExternalId;
        @JsonProperty("broadcaster.platform.id")
        public int broadcasterPlatformId;
        @JsonProperty("broadcaster.broadcast_defaults.language.id")
        public int broadcasterDefaultLanguageId;
        public List<Broadcast> broadcasts;
        public boolean official;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Broadcast {
        @JsonProperty("external_id")
        public String externalId;
        @JsonProperty("language.id")
        public int languageId;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GameVersion {
        public Release release;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Release {
        public String uuid;
        public String date;
        public String description;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Coverage {
        public CoverageData data;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CoverageData {
        public CoverageType live;
        public CoverageType realtime;
        public CoverageType postgame;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CoverageType {
        public CoverageStatus api;
        public CoverageStatus cv;
        public CoverageStatus server;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CoverageStatus {
        public String expectation;
        public String fact;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Format {
        @JsonProperty("best_of")
        public int bestOf;
    }
}