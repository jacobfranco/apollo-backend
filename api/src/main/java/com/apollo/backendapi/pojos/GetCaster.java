package com.apollo.backendapi.pojos;

import com.apollo.backend.data.Caster;

public class GetCaster {
    public boolean primary;
    public int casterId;

    public GetCaster(Caster c) {
        this.primary = c.isPrimary();
        this.casterId = c.getCasterId();
    }
}