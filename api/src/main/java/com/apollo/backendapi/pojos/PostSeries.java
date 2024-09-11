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
    public PostBracketPosition bracketPosition;
    public List<PostParticipant> participants;
    @JsonProperty("tournament.id")
    public int tournamentId;
    @JsonProperty("substage.id")
    public int substageId;
    @JsonProperty("game.id")
    public int gameId;
    @JsonProperty("matches[*].id")
    public List<Integer> matchIds;
    public List<PostCaster> casters;
    public List<PostBroadcaster> broadcasters;
    @JsonProperty("has_incident_report")
    public boolean hasIncidentReport;
    public PostCoverage coverage;
    public PostFormat format;
    @JsonProperty("game_version")
    public PostGameVersion gameVersion;
    @JsonProperty("resource_version")
    public long resourceVersion;

    @JsonProperty("created_at")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
    public Instant createdAt;

    @JsonProperty("updated_at")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
    public Instant updatedAt;
}