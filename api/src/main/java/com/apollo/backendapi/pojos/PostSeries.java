package com.apollo.backendapi.pojos;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;
import java.util.Collections;
import java.util.stream.Collectors;

@JsonIgnoreProperties(ignoreUnknown = true)
public class PostSeries {
    public int id;
    public String title;

    public Instant start;

    public Instant end;

    @JsonProperty("postponed_from")
    public Instant postponedFrom;

    @JsonProperty("deleted_at")
    public Instant deletedAt;

    public String lifecycle;
    public int tier;

    @JsonProperty("best_of")
    public int bestOf;

    public List<PostChainInfo> chain = Collections.emptyList();
    public boolean streamed;

    @JsonProperty("bracket_position")
    public PostBracketPosition bracketPosition;

    public List<PostParticipant> participants = Collections.emptyList();

    public PostTournamentInfo tournament;
    public PostSubstageInfo substage;
    public PostGameInfo game;

    public List<PostMatchInfo> matches = Collections.emptyList();
    public List<PostCaster> casters = Collections.emptyList();
    public List<PostBroadcaster> broadcasters = Collections.emptyList();

    @JsonProperty("has_incident_report")
    public boolean hasIncidentReport;

    public PostCoverage coverage;
    public PostFormat format;

    @JsonProperty("game_version")
    public PostGameVersion gameVersion;

    @JsonProperty("resource_version")
    public long resourceVersion;

    @JsonProperty("created_at")
    public Instant createdAt;

    @JsonProperty("updated_at")
    public Instant updatedAt;

    // Getter methods TODO: Maybe we can take this out
    public List<Integer> getChainIds() {
        return chain.stream().map(c -> c.id).collect(Collectors.toList());
    }

    public int getTournamentId() {
        return tournament != null ? tournament.id : 0;
    }

    public int getSubstageId() {
        return substage != null ? substage.id : 0;
    }

    public int getGameId() {
        return game != null ? game.id : 0;
    }

    public List<Integer> getMatchIds() {
        return matches.stream().map(m -> m.id).collect(Collectors.toList());
    }
}
