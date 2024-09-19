package com.apollo.backendapi.pojos;

import com.apollo.backend.data.Participant;

public class GetParticipant {
    public int seed;
    public int score;
    public boolean forfeit;
    public GetRoster roster;
    public boolean winner;
    public GetParticipantStats stats;

    public GetParticipant(Participant participant) {
        this.seed = participant.getSeed();
        this.score = participant.getScore();
        this.forfeit = participant.isForfeit();
        this.winner = participant.isWinner();
        this.roster = participant.isSetRoster() ? new GetRoster(participant.getRoster()) : null;
        this.stats = participant.isSetStats() ? new GetParticipantStats(participant.getStats()) : null;
    }
}