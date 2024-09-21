package com.apollo.backendapi.pojos;

import java.util.List;
import com.apollo.backend.data.Roster;

public class GetRoster {
    public int id;
    public int teamId;
    public List<Integer> playerIds;
    public int gameId;
    public GetTeam team;
    public List<GetPlayer> players;

    public GetRoster(Roster roster) {
        this.id = roster.getId();
        this.teamId = roster.getTeamId();
        this.playerIds = roster.getPlayerIds();
        this.gameId = roster.getGameId();
    }
}