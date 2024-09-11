package com.apollo.backendapi.pojos;

import com.apollo.backend.data.Participant;

public class GetParticipant {
    public int seed;
    public int score;
    public boolean forfeit;
    public int rosterId;
    public boolean winner;
    public GetParticipantStats stats;

    public GetParticipant(Participant p) {
        this.seed = p.getSeed();
        this.score = p.getScore();
        this.forfeit = p.isForfeit();
        this.rosterId = p.getRosterId();
        this.winner = p.isWinner();
        this.stats = p.isSetStats() ? new GetParticipantStats(p.getStats()) : null;
    }
}