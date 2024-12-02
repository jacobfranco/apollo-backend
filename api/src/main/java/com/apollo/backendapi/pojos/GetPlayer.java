package com.apollo.backendapi.pojos;

import com.apollo.backend.data.LolPlayerAggStats;
import com.apollo.backend.data.LolPlayerSummary;
import com.apollo.backend.data.Player;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
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
    public String role;
    public List<Integer> teamIds;
    public List<GetSocialMediaAccount> socialMediaAccounts;
    public long resourceVersion;
    public GetLolPlayerAggStats aggStats;
    public GetPlayerMatchStats matchStats;
    public List<GetLolPlayerMatchStats> lolSeasonStats;

    @JsonIgnore
    public Map<Integer, GetAsset> assetMap;

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
        this.role = mapRoleIdToRoleName(player.isSetRoleId() ? player.getRoleId() : null);
        this.teamIds = player.getTeamIds();
        this.socialMediaAccounts = player.getSocialMediaAccounts().stream().map(GetSocialMediaAccount::new)
                .collect(Collectors.toList());
        this.resourceVersion = player.getResourceVersion();
    }

    public GetPlayer(Player player, LolPlayerAggStats aggStats, List<LolPlayerSummary> lolSeasonStats,
            Map<Integer, GetAsset> assetMap) {
        this(player);

        if (aggStats != null) {
            this.aggStats = new GetLolPlayerAggStats(aggStats);
        }

        if (lolSeasonStats != null && !lolSeasonStats.isEmpty()) {
            this.lolSeasonStats = lolSeasonStats.stream()
                    .map(stat -> new GetLolPlayerMatchStats(stat, assetMap))
                    .collect(Collectors.toList());
        } else {
            this.lolSeasonStats = Collections.emptyList();
        }

        if (assetMap != null && !assetMap.isEmpty()) {
            this.assetMap = assetMap;
        } else {
            this.assetMap = Collections.emptyMap();
        }
    }

    // Map roleId to role name
    private String mapRoleIdToRoleName(Integer roleId) {
        if (roleId == null) {
            return "unknown";
        }

        switch (roleId) {
            case 1:
                return "top";
            case 2:
                return "jungle";
            case 3:
                return "mid";
            case 4:
                return "bot";
            case 5:
                return "support";
            default:
                return "unassigned";
        }
    }
}
