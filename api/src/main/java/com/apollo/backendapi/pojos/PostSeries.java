package com.apollo.backendapi.pojos;

import java.time.Instant;
import java.util.List;

public class PostSeries {
    public int id;
    public String title;
    public Instant start;
    public Instant end;
    public String lifecycle;
    public int tier;
    public int bestOf;
    public List<ChainItem> chain;
    public boolean streamed;
    public BracketPosition bracketPosition;
    public Tournament tournament;
    public Substage substage;
    public Game game;
    public Format format;
    public Instant postponedFrom;
    public Instant deletedAt;
    public List<Participant> participants;
    public List<Match> matches;
    public List<Caster> casters;
    public List<Broadcaster> broadcasters;
    public boolean hasIncidentReport;
    public GameVersion gameVersion;
    public Coverage coverage;
    public long resourceVersion;

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

    public static class Format {
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

    public static class BroadcasterInfo {
        public int id;
        public String name;
        public String externalId;
        public Platform platform;
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

    public static class Broadcast {
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