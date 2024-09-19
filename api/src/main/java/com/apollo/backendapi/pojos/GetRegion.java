package com.apollo.backendapi.pojos;

import com.apollo.backend.data.Region;

public class GetRegion {
    public int id;
    public String name;
    public String abbreviation;
    public GetCountry country;

    public GetRegion(Region region) {
        this.id = region.getId();
        this.name = region.getName();
        this.abbreviation = region.getAbbreviation();
        this.country = new GetCountry(region.getCountry());
    }
}