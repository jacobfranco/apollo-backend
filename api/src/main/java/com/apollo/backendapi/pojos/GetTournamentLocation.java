package com.apollo.backendapi.pojos;

import com.apollo.backend.data.TournamentLocation;
import java.util.List;
import java.util.stream.Collectors;

public class GetTournamentLocation {
    public GetHost host;
    public List<GetParticipant> participants;

    public GetTournamentLocation(TournamentLocation location) {
        this.host = location.isSetHost() ? new GetHost(location.getHost()) : null;
        this.participants = location.getParticipants().stream()
                .map(GetParticipant::new)
                .collect(Collectors.toList());
    }
}
