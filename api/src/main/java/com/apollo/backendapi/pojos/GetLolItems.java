package com.apollo.backendapi.pojos;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import com.apollo.backend.data.LolItems;

public class GetLolItems {
    public List<GetLolItem> inventory;
    public List<GetLolItem> trinketSlot;

    public GetLolItems(LolItems items) {
        this.inventory = Optional.ofNullable(items.getInventory())
                .orElse(Collections.emptyList())
                .stream()
                .map(GetLolItem::new)
                .collect(Collectors.toList());
        this.trinketSlot = Optional.ofNullable(items.getTrinketSlot())
                .orElse(Collections.emptyList())
                .stream()
                .map(GetLolItem::new)
                .collect(Collectors.toList());
    }
}