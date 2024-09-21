package com.apollo.backendapi.pojos;

import com.apollo.backend.data.LolInhibitors;

public class GetLolInhibitors {
    public GetLolInhibitor top;
    public GetLolInhibitor mid;
    public GetLolInhibitor bot;

    public GetLolInhibitors(LolInhibitors inhibitors) {
        this.top = new GetLolInhibitor(inhibitors.getTop());
        this.mid = new GetLolInhibitor(inhibitors.getMid());
        this.bot = new GetLolInhibitor(inhibitors.getBot());
    }
}