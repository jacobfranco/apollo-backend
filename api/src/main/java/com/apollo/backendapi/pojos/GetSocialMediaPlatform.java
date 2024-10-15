package com.apollo.backendapi.pojos;

import com.apollo.backend.data.SocialMediaPlatform;

public class GetSocialMediaPlatform {
    public int id;
    public String name;
    public String slug;

    public GetSocialMediaPlatform(SocialMediaPlatform platform) {
        this.id = platform.getId();
        this.name = platform.getName();
        this.slug = platform.getSlug();
    }
}