package com.apollo.backendapi.pojos;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import com.apollo.backend.data.LolTeamSummary;

public class GetLolTeamSummary {
    public int matchId;
    public GetLolRoster roster;
    public int score;
    public boolean isWinner;
    public int goldEarned;
    public int turretsDestroyed;
    public int inhibitorsDestroyed;
    public GetLolFaction faction;
    public GetLolStructures structures;
    public GetLolCreeps creeps;
    public List<GetLolPlayerSummary> players;

    public GetLolTeamSummary(LolTeamSummary teamSummary, Map<Integer, GetAsset> assetMap) {
        this.matchId = teamSummary.getMatchId();
        this.roster = new GetLolRoster(teamSummary.getRoster());
        this.score = teamSummary.getScore();
        this.isWinner = teamSummary.isIsWinner();
        this.goldEarned = teamSummary.getGoldEarned();
        this.turretsDestroyed = teamSummary.getTurretsDestroyed();
        this.inhibitorsDestroyed = teamSummary.getInhibitorsDestroyed();
        this.faction = new GetLolFaction(teamSummary.getFaction());
        this.structures = new GetLolStructures(teamSummary.getStructures());
        this.creeps = new GetLolCreeps(teamSummary.getCreeps(), assetMap);
        this.players = teamSummary.getPlayers().stream()
                .map(player -> new GetLolPlayerSummary(player, assetMap))
                .collect(Collectors.toList());
    }
}