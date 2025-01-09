package com.apollo.backendapi.pojos;

import com.apollo.backend.data.LolRoster;

public class GetLolRoster {
    public int id;

    public GetLolRoster(LolRoster roster) {
        this.id = roster.getId();
    }
}