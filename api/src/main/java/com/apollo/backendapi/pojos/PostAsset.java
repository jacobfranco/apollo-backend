package com.apollo.backendapi.pojos;

import java.util.List;

public class PostAsset {
    public int id;
    public String name;
    public PostGame game;
    public String category;
    public String subcategory;
    public String external_id;
    public List<PostImage> images;
}
