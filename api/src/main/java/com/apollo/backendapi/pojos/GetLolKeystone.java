package com.apollo.backendapi.pojos;

import java.util.Map;

import com.apollo.backend.data.LolKeystone;

public class GetLolKeystone {
    public GetAsset keystone;

    public GetLolKeystone(LolKeystone keystone, Map<Integer, GetAsset> assetMap) {
        this.keystone = assetMap.get(keystone.getId());
    }
}