package com.apollo.backendapi.pojos;

import com.apollo.backend.data.GameVersion;

public class GetGameVersion {
    public GetRelease release;

    public GetGameVersion(GameVersion gv) {
        this.release = gv.isSetRelease() ? new GetRelease(gv.getRelease()) : null;
    }
}