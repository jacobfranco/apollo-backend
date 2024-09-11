package com.apollo.backendapi.pojos;

import com.apollo.backend.data.Broadcast;

public class GetBroadcast {
    public String externalId;
    public int languageId;

    public GetBroadcast(Broadcast b) {
        this.externalId = b.getExternalId();
        this.languageId = b.getLanguageId();
    }
}