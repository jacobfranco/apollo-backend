package com.apollo.backendapi.pojos;

import com.apollo.backend.data.LolFriendlyRevives;

public class GetLolFriendlyRevives {
    public GetLolReviveCount given;
    public GetLolReviveCount taken;

    public GetLolFriendlyRevives(LolFriendlyRevives friendlyRevives) {
        this.given = new GetLolReviveCount(friendlyRevives.getGiven());
        this.taken = new GetLolReviveCount(friendlyRevives.getTaken());
    }
}