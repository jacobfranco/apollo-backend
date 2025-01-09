package com.apollo.backendapi.pojos;

import java.util.List;
import java.util.stream.Collectors;
import com.apollo.backend.data.StreamingPlatform;

public class GetStreamingPlatform {
    public int id;
    public String name;
    public String color;
    public List<GetImage> images;

    public GetStreamingPlatform(StreamingPlatform platform) {
        this.id = platform.getId();
        this.name = platform.getName();
        this.color = platform.getColor();
        this.images = platform.isSetImages()
                ? platform.getImages().stream().map(GetImage::new).collect(Collectors.toList())
                : null;
    }
}
