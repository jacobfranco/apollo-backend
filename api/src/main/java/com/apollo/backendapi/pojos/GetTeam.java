package com.apollo.backendapi.pojos;

import com.apollo.backend.data.LolTeamAggStats;
import com.apollo.backend.data.LolTeamSummary;
import com.apollo.backend.data.Team;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class GetTeam {
    public int id;
    public String name;
    public String abbreviation;
    public List<String> alsoKnownAs;
    public Instant deletedAt;
    public boolean active;
    public List<GetImage> images;
    public GetRegion region;
    public List<GetSocialMediaAccount> socialMediaAccounts;
    public GetStandingRoster standingRoster;
    public int gameId;
    public Integer organizationId;
    public long resourceVersion;
    public GetTeamMatchStats matchStats;
    public GetTeamAggStats aggStats;
    public List<GetTeamMatchStats> lolSeasonStats;

    @JsonIgnore
    public Map<Integer, GetAsset> assetMap;

    public GetTeam(Team team) {
        this.id = team.getId();
        this.name = team.getName();
        this.abbreviation = team.getAbbreviation();
        this.alsoKnownAs = team.getAlsoKnownAs();
        this.deletedAt = team.isSetDeletedAt() ? Instant.ofEpochMilli(team.getDeletedAt()) : null;
        this.active = team.isActive();
        this.images = team.getImages().stream().map(GetImage::new).collect(Collectors.toList());
        this.region = team.isSetRegion() ? new GetRegion(team.getRegion()) : null;
        this.socialMediaAccounts = team.getSocialMediaAccounts().stream().map(GetSocialMediaAccount::new)
                .collect(Collectors.toList());
        this.standingRoster = team.isSetStandingRoster() ? new GetStandingRoster(team.getStandingRoster()) : null;
        this.gameId = team.getGameId();
        this.organizationId = team.isSetOrganizationId() ? team.getOrganizationId() : null;
        this.resourceVersion = team.getResourceVersion();
    }

    public GetTeam(Team team, LolTeamAggStats aggStats, List<LolTeamSummary> lolSeasonStats,
            Map<Integer, GetAsset> assetMap) {
        this(team);

        // Initialize lolSeasonStats first
        if (lolSeasonStats != null) {
            this.lolSeasonStats = lolSeasonStats.stream()
                    .map(stat -> new GetTeamMatchStats(stat, assetMap))
                    .collect(Collectors.toList());
        } else {
            this.lolSeasonStats = Collections.emptyList();
        }

        this.aggStats = new GetTeamAggStats(aggStats);

        this.assetMap = assetMap;
    }
}