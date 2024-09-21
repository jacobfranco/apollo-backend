package com.apollo.backendapi.pojos;

import java.util.List;
import java.util.stream.Collectors;

import com.apollo.backend.data.Asset;

public class GetAsset {
    public int id;
    public String name;
    public GetGame game;
    public String category;
    public String subcategory;
    public String externalId;
    public List<GetImage> images;

    public GetAsset(Asset asset) {
        this.id = asset.getId();
        this.name = asset.getName();
        this.game = asset.getGame() != null ? new GetGame(asset.getGame()) : null;
        this.category = asset.getCategory();
        this.subcategory = asset.getSubcategory();
        this.externalId = asset.getExternalId();
        this.images = asset.getImages() != null
                ? asset.getImages().stream().map(GetImage::new).collect(Collectors.toList())
                : null;
    }
}
