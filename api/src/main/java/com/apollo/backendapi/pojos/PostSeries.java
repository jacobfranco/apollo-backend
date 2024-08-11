package com.apollo.backendapi.pojos;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

// TODO: Remove unnecessary fields when we know 

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
    public List<ChainItem> chain;
    public boolean streamed;
    @JsonProperty("bracket_position")
    public BracketPosition bracketPosition;
    public List<Participant> participants;
    public Tournament tournament;
    public Substage substage;
    public Game game;
    public List<Match> matches;
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

    public static class ChainItem {
        public int id;
    }

    public static class Tournament {
        public int id;
    }

    public static class Substage {
        public int id;
    }

    public static class Game {
        public int id;
    }

    public static class BracketPosition {
        public String part;
        public int col;
        public int offset;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Format {
        @JsonProperty("best_of")
        public int bestOf;
    }

    public static class Participant {
        public int seed;
        public int score;
        public boolean forfeit;
        public Roster roster;
        public boolean winner;
        public ParticipantStats stats;
    }

    public static class Roster {
        public int id;
    }

    public static class ParticipantStats {
        public int kills;
        public int placement;
    }

    public static class Match {
        public int id;
    }

    public static class Caster {
        public boolean primary;
        public CasterInfo caster;
    }

    public static class CasterInfo {
        public int id;
    }

    public static class Broadcaster {
        public BroadcasterInfo broadcaster;
        public List<Broadcast> broadcasts;
        public boolean official;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class BroadcasterInfo {
        public int id;
        public String name;
        @JsonProperty("external_id")
        public String externalId;
        public Platform platform;
        @JsonProperty("broadcast_defaults")
        public BroadcastDefaults broadcastDefaults;
    }

    public static class Platform {
        public int id;
    }

    public static class BroadcastDefaults {
        public Language language;
    }

    public static class Language {
        public int id;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Broadcast {
        @JsonProperty("external_id")
        public String externalId;
        public Language language;
    }

    public static class GameVersion {
        public Release release;
    }

    public static class Release {
        public String uuid;
        public String date;
        public String description;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Coverage {
        public CoverageData data;
    }

    public static class CoverageData {
        public CoverageType live;
        public CoverageType realtime;
        public CoverageType postgame;
    }

    public static class CoverageType {
        public CoverageStatus api;
        public CoverageStatus cv;
        public CoverageStatus server;
    }

    public static class CoverageStatus {
        public String expectation;
        public String fact;
    }
}