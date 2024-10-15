package com.apollo.backendapi.pojos;

import java.time.Instant;
import java.util.List;

public class PostStream {
    public int id;
    public String username;
    public String displayName;
    public String statusText;
    public int viewerCount;
    public boolean online;
    public Instant lastOnline;
    public List<PostImage> images;
    public PostStreamingPlatform platform;
}
