package com.apollo.backendapi.pojos;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;
import com.apollo.backend.data.Stream;

public class GetStream {
    public int id;
    public String username;
    public String displayName;
    public String statusText;
    public int viewerCount;
    public boolean online;
    public Instant lastOnline;
    public List<GetImage> images;
    public GetStreamingPlatform platform;

    public GetStream(Stream stream) {
        this.id = stream.getId();
        this.username = stream.getUsername();
        this.displayName = stream.getDisplayName();
        this.statusText = stream.getStatusText();
        this.viewerCount = stream.getViewerCount();
        this.online = stream.isOnline();
        this.lastOnline = stream.isSetLastOnline() ? Instant.ofEpochMilli(stream.getLastOnline()) : null;
        this.images = stream.isSetImages() ? stream.getImages().stream().map(GetImage::new).collect(Collectors.toList())
                : null;
        this.platform = stream.getPlatform() != null ? new GetStreamingPlatform(stream.getPlatform()) : null;
    }
}
