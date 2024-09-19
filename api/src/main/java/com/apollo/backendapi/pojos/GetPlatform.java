package com.apollo.backendapi.pojos;

import com.apollo.backend.data.Platform;

public class GetPlatform {
    public int id;
    public String name;
    public String slug;

    public GetPlatform(Platform platform) {
        this.id = platform.getId();
        this.name = platform.getName();
        this.slug = platform.getSlug();
    }
}