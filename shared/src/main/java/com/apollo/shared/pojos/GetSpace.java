package com.apollo.shared.pojos;

import java.util.*;

import com.rpl.rama.RamaSerializable;

public class GetSpace implements RamaSerializable {
    public String name;
    public String url;
    public String imageUrl;

    public static class HistoryItem {
        public String day; // unix timestamp
        public String uses; // counted usage
        public String accounts; // number of accounts using the space

        public HistoryItem() {
        }

        public HistoryItem(long day, int uses, int accounts) {
            this.day = (day * 60 * 60 * 24) + "";
            this.uses = uses + "";
            this.accounts = accounts + "";
        }
    }

    public List<HistoryItem> history = new ArrayList<>();
    public Boolean following; // optional
    public String id;
    public boolean trendable;
    public boolean usable;
    public boolean requires_review;

    public GetSpace(String name, String id, String imageUrl) {
        this.name = name;
        this.url = "/s/" + id;
        this.id = id;
        this.imageUrl = imageUrl;
    }
}
