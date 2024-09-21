package com.apollo.backendapi.pojos;

import com.apollo.backend.data.LolItem;

public class GetLolItem {
    public int id;
    public int slot;

    public GetLolItem(LolItem item) {
        this.id = item.getId();
        this.slot = item.getSlot();
    }
}