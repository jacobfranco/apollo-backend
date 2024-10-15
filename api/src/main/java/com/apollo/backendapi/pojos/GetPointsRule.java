package com.apollo.backendapi.pojos;

import com.apollo.backend.data.PointsRule;

public class GetPointsRule {
    public int win;
    public int draw;
    public int loss;
    public String scope;

    public GetPointsRule(PointsRule rule) {
        this.win = rule.getWin();
        this.draw = rule.getDraw();
        this.loss = rule.getLoss();
        this.scope = rule.getScope();
    }
}
