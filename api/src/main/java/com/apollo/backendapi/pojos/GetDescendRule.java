package com.apollo.backendapi.pojos;

import com.apollo.backend.data.DescendRule;

public class GetDescendRule {
    public int number;
    public Integer substageId; // Optional field

    public GetDescendRule(DescendRule rule) {
        this.number = rule.getNumber();
        this.substageId = rule.isSetSubstageId() ? rule.getSubstageId() : null;
    }
}
