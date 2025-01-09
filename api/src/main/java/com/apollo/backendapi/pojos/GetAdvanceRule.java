package com.apollo.backendapi.pojos;

import com.apollo.backend.data.AdvanceRule;

public class GetAdvanceRule {
    public int number;
    public Integer substageId; // Optional field

    public GetAdvanceRule(AdvanceRule rule) {
        this.number = rule.getNumber();
        this.substageId = rule.isSetSubstageId() ? rule.getSubstageId() : null;
    }
}
