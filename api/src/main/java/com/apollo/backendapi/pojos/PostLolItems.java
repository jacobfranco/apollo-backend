package com.apollo.backendapi.pojos;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class PostLolItems {
    public List<PostLolItem> inventory;
    public List<PostLolItem> trinket_slot;
}