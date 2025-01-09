package com.apollo.backendapi.pojos;

import java.util.*;

import com.apollo.backendapi.ApolloApiHelpers;
import com.rpl.rama.RamaSerializable;

public class GetSpace implements RamaSerializable {
    public String id;
    public String name;
    public String linkUrl;
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

    public GetSpace(String id, String name) {
        this.id = id; // Id is stored as "lol" or "cs"
        this.name = name;
        this.linkUrl = "/s/" + id;
        this.imageUrl = "https://yoapollo.s3.us-east-2.amazonaws.com/spaces/" + id + ".webp";
    }
}
