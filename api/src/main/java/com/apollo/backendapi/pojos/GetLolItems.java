package com.apollo.backendapi.pojos;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.Collections;
import com.apollo.backend.data.LolItems;

public class GetLolItems {
    public List<GetLolItem> inventory;
    public List<GetLolItem> trinketSlot;

    public GetLolItems(LolItems items, Map<Integer, GetAsset> assetMap) {
        this.inventory = items.getInventory() != null
                ? items.getInventory().stream()
                        .map(item -> new GetLolItem(item, assetMap))
                        .collect(Collectors.toList())
                : Collections.emptyList();

        this.trinketSlot = items.getTrinketSlot() != null
                ? items.getTrinketSlot().stream()
                        .map(item -> new GetLolItem(item, assetMap))
                        .collect(Collectors.toList())
                : Collections.emptyList();
    }
}