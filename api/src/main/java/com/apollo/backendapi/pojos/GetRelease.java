package com.apollo.backendapi.pojos;

import com.apollo.backend.data.Release;

public class GetRelease {
    public String uuid;
    public String date;
    public String description;

    public GetRelease(Release r) {
        this.uuid = r.getUuid();
        this.date = r.getDate();
        this.description = r.getDescription();
    }
}