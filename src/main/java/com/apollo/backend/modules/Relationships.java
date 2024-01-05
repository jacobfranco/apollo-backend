package com.apollo.backend.modules;

import com.apollo.backend.ApolloHelpers.*;

import com.rpl.rama.*;

public class Relationships implements RamaModule {

    @Override
    public void define(Setup setup, Topologies topologies) {
        setup.declareDepot("*authCodeDepot", Depot.hashBy(ExtractCode.class));
    }
    
}
