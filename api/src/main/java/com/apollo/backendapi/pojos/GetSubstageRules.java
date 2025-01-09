package com.apollo.backendapi.pojos;

import com.apollo.backend.data.SubstageRules;

public class GetSubstageRules {
    public GetAdvanceRule advance;
    public GetDescendRule descend;
    public GetPointsRule points;

    public GetSubstageRules(SubstageRules rules) {
        this.advance = rules.isSetAdvance() ? new GetAdvanceRule(rules.getAdvance()) : null;
        this.descend = rules.isSetDescend() ? new GetDescendRule(rules.getDescend()) : null;
        this.points = rules.isSetPoints() ? new GetPointsRule(rules.getPoints()) : null;
    }
}
