package com.apollo.backendapi.pojos;

import com.apollo.backend.data.Image;

public class GetImage {
    public int id;
    public String type;
    public String url;
    public String thumbnail;
    public boolean fallback;

    public GetImage(Image image) {
        this.id = image.getId();
        this.type = image.getType();
        this.url = image.getUrl();
        this.thumbnail = image.getThumbnail();
        this.fallback = image.isFallback();
    }
}