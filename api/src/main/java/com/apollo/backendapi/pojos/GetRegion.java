package com.apollo.backendapi.pojos;

import com.apollo.backend.data.Region;

public class GetRegion {
    public Integer id;
    public String name;
    public String abbreviation;
    public GetCountry country;

    public GetRegion(Region region) {
        if (region != null) {
            this.id = region.getId();
            this.name = region.getName();
            this.abbreviation = region.getAbbreviation();
            this.country = region.getCountry() != null ? new GetCountry(region.getCountry()) : null;
        }
    }
}