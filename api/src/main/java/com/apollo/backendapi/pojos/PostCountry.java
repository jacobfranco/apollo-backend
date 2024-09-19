package com.apollo.backendapi.pojos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class PostCountry {
    public int id;
    public String name;
    public String abbreviation;
    public List<PostImage> images;
}