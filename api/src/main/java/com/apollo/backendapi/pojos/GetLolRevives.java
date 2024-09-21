package com.apollo.backendapi.pojos;

import com.apollo.backend.data.LolRevives;

public class GetLolRevives {
    public GetLolFriendlyRevives friendly;

    public GetLolRevives(LolRevives revives) {
        this.friendly = new GetLolFriendlyRevives(revives.getFriendly());
    }
}