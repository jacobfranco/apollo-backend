package com.apollo.backendapi.pojos;

import com.apollo.backend.data.LolPosition;

public class GetLolPosition {
    public double x;
    public double y;

    public GetLolPosition(LolPosition position) {
        this.x = position.getX();
        this.y = position.getY();
    }
}