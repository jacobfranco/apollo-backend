package com.apollo.backendapi.pojos;

import java.util.Map;

import com.apollo.backend.data.LolItem;

public class GetLolItem {
    public GetAsset item;
    public int slot;

    public GetLolItem(LolItem itemSlot, Map<Integer, GetAsset> assetMap) {
        this.item = assetMap.get(itemSlot.getId());
        this.slot = itemSlot.getSlot();
    }
}