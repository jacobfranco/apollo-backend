package com.apollo.backendapi.pojos;

import com.apollo.backend.data.Host;

public class GetHost {
    public int id;
    public String name;
    public String abbreviation;
    public GetCountry country;

    public GetHost(Host host) {
        this.id = host.getId();
        this.name = host.getName();
        this.abbreviation = host.getAbbreviation();
        this.country = host.isSetCountry() ? new GetCountry(host.getCountry()) : null;
    }
}
