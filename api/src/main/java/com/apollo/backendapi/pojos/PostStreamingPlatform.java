package com.apollo.backendapi.pojos;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class PostStreamingPlatform {
    public int id;
    public String name;
    public String color;
    public List<PostImage> images;
}
