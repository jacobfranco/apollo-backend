package com.apollo.backendapi.pojos;

import java.util.List;
import java.util.stream.Collectors;
import com.apollo.backend.data.LolNeutralCreepKills;

public class GetLolNeutralCreepKills {
    public List<GetLolEliteCreepKills> perEliteType;

    public GetLolNeutralCreepKills(LolNeutralCreepKills neutralCreepKills) {
        this.perEliteType = neutralCreepKills.getPerEliteType().stream()
                .map(GetLolEliteCreepKills::new)
                .collect(Collectors.toList());
    }
}