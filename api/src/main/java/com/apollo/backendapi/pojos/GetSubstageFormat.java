package com.apollo.backendapi.pojos;

import com.apollo.backend.data.SubstageFormat;
import java.util.List;
import java.util.stream.Collectors;

public class GetSubstageFormat {
    public GetPointsRule points;
    public List<GetMovement> movements;

    public GetSubstageFormat(SubstageFormat format) {
        this.points = format.isSetPoints() ? new GetPointsRule(format.getPoints()) : null;
        this.movements = format.getMovements().stream()
                .map(GetMovement::new)
                .collect(Collectors.toList());
    }
}
