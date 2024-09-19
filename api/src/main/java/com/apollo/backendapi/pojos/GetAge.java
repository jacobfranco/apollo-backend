package com.apollo.backendapi.pojos;

import com.apollo.backend.data.Age;

public class GetAge {
    public String precision;
    public int years;

    public GetAge(Age age) {
        if (age != null) {
            this.precision = age.getPrecision();
            this.years = age.getYears();
        } else {
            this.precision = "unknown";
            this.years = 0;
        }
    }
}
