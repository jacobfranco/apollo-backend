package com.apollo.backendapi.pojos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class PostImage {
    public int id;
    public String type;
    public String url;
    public String thumbnail;
    public boolean fallback;
}