package com.apollo.backendapi.pojos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class PostPlayer {
    public int id;
    @JsonProperty("first_name")
    public String firstName;
    @JsonProperty("last_name")
    public String lastName;
    @JsonProperty("nick_name")
    public String nickName;
    @JsonProperty("also_known_as")
    public List<String> alsoKnownAs;
    public PostAge age;
    @JsonProperty("deleted_at")
    public Instant deletedAt;
    public boolean active;
    public List<PostImage> images;
    public PostRegion region;
    public PostGameInfo game;
    public PostRace race;
    public PostRole role;
    public List<PostTeamInfo> teams;
    @JsonProperty("social_media_accounts")
    public List<PostSocialMediaAccount> socialMediaAccounts;
    @JsonProperty("resource_version")
    public long resourceVersion;
}