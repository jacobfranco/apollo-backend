package com.apollo.backendapi.pojos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class PostTeam {
    public int id;
    public String name;
    public String abbreviation;
    @JsonProperty("also_known_as")
    public List<String> alsoKnownAs;
    @JsonProperty("deleted_at")
    public Instant deletedAt;
    public boolean active;
    public List<PostImage> images;
    public PostRegion region;
    @JsonProperty("social_media_accounts")
    public List<PostSocialMediaAccount> socialMediaAccounts;
    @JsonProperty("standing_roster")
    public PostStandingRoster standingRoster;
    public PostGameInfo game;
    public PostOrganization organization;
    @JsonProperty("resource_version")
    public long resourceVersion;

}