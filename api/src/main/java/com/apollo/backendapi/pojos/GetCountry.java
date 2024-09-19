package com.apollo.backendapi.pojos;

import com.apollo.backend.data.Country;
import java.util.List;
import java.util.stream.Collectors;

public class GetCountry {
    public int id;
    public String name;
    public String abbreviation;
    public List<GetImage> images;

    public GetCountry(Country country) {
        this.id = country.getId();
        this.name = country.getName();
        this.abbreviation = country.getAbbreviation();
        this.images = country.getImages().stream().map(GetImage::new).collect(Collectors.toList());
    }
}