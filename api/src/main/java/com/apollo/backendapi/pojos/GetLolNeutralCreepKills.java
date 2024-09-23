package com.apollo.backendapi.pojos;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import com.apollo.backend.data.LolNeutralCreepKills;

public class GetLolNeutralCreepKills {
    public List<GetLolEliteCreepKills> perEliteType;

    public GetLolNeutralCreepKills(LolNeutralCreepKills neutralCreepKills, Map<Integer, GetAsset> assetMap) {
        this.perEliteType = neutralCreepKills.getPerEliteType().stream()
                .map(elite -> new GetLolEliteCreepKills(elite, assetMap))
                .collect(Collectors.toList());
    }
}