package com.apollo.backendapi.pojos;

import com.apollo.backend.data.Game;

public class GetGame {
    public int id;
    public String name;
    public String shortName;
    public String slug;

    public GetGame(Game game) {
        this.id = game.getId();
        this.name = game.getName();
        this.shortName = game.getShortName();
        this.slug = game.getSlug();
    }
}
