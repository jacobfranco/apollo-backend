package com.apollo.backendapi.pojos;

import java.util.List;

import com.apollo.backend.data.Roster;

public class GetRoster {
    public int id;
    public int teamId;
    public int lineUpId;
    public List<Integer> playerIds;
    public int gameId;

    public GetRoster(Roster roster) {
        this.id = roster.getId();
        this.teamId = roster.getTeamId();
        this.lineUpId = roster.getLineUpId();
        this.playerIds = roster.getPlayerIds();
        this.gameId = roster.getGameId();
    }
}
