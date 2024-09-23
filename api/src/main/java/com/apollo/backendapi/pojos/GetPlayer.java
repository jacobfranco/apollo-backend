package com.apollo.backendapi.pojos;

import com.apollo.backend.data.Player;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

public class GetPlayer {
    public int id;
    public String firstName;
    public String lastName;
    public String nickName;
    public List<String> alsoKnownAs;
    public GetAge age;
    public Instant deletedAt;
    public boolean active;
    public List<GetImage> images;
    public GetRegion region;
    public int gameId;
    public Integer raceId;
    public Integer roleId;
    public List<Integer> teamIds;
    public List<GetSocialMediaAccount> socialMediaAccounts;
    public long resourceVersion;
    public GetLolPlayerSummary lolStats;
    public GetPlayerMatchStats matchStats;

    public GetPlayer(Player player) {
        this.id = player.getId();
        this.firstName = player.getFirstName();
        this.lastName = player.getLastName();
        this.nickName = player.getNickName();
        this.alsoKnownAs = player.getAlsoKnownAs();
        this.age = new GetAge(player.getAge());
        this.deletedAt = player.isSetDeletedAt() ? Instant.ofEpochMilli(player.getDeletedAt()) : null;
        this.active = player.isActive();
        this.images = player.getImages().stream().map(GetImage::new).collect(Collectors.toList());
        this.region = new GetRegion(player.getRegion());
        this.gameId = player.getGameId();
        this.raceId = player.isSetRaceId() ? player.getRaceId() : null;
        this.roleId = player.isSetRoleId() ? player.getRoleId() : null;
        this.teamIds = player.getTeamIds();
        this.socialMediaAccounts = player.getSocialMediaAccounts().stream().map(GetSocialMediaAccount::new)
                .collect(Collectors.toList());
        this.resourceVersion = player.getResourceVersion();
    }
}