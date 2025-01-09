package com.apollo.backendapi.pojos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class PostRegion {
    public int id;
    public String name;
    public String abbreviation;
    public PostCountry country;
}