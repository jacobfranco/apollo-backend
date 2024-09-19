package com.apollo.backendapi.pojos;

import com.apollo.backend.data.Team;
import java.time.Instant;
import java.util.List;
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

    public GetTeam(Team team) {
        this.id = team.getId();
        this.name = team.getName();
        this.abbreviation = team.getAbbreviation();
        this.alsoKnownAs = team.getAlsoKnownAs();
        this.deletedAt = team.isSetDeletedAt() ? Instant.ofEpochMilli(team.getDeletedAt()) : null;
        this.active = team.isActive();
        this.images = team.getImages().stream().map(GetImage::new).collect(Collectors.toList());
        this.region = new GetRegion(team.getRegion());
        this.socialMediaAccounts = team.getSocialMediaAccounts().stream().map(GetSocialMediaAccount::new)
                .collect(Collectors.toList());
        this.standingRoster = team.isSetStandingRoster() ? new GetStandingRoster(team.getStandingRoster()) : null;
        this.gameId = team.getGameId();
        this.organizationId = team.isSetOrganizationId() ? team.getOrganizationId() : null;
        this.resourceVersion = team.getResourceVersion();
    }
}