package com.apollo.backendapi.pojos;

import java.util.Map;

import com.apollo.backend.data.Participant;
import com.apollo.backend.data.Roster;

public class GetParticipant {
    public int seed;
    public int score;
    public boolean forfeit;
    public boolean winner;
    public GetRoster roster;
    public GetParticipantStats stats;

    public GetParticipant(Participant participant, Map<Integer, Roster> rosterMap) {
        this.seed = participant.getSeed();
        this.score = participant.getScore();
        this.forfeit = participant.isForfeit();
        this.winner = participant.isWinner();

        // Get the roster from the map
        Roster roster = rosterMap.get(participant.getRosterId());
        this.roster = roster != null ? new GetRoster(roster) : null;

        // Initialize stats if available
        this.stats = participant.isSetStats() ? new GetParticipantStats(participant.getStats()) : null;
    }
}
