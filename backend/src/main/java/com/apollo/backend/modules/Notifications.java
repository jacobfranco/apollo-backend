package com.apollo.backend.modules;

import com.rpl.rama.*;

public class Notifications implements RamaModule {

    @Override
    public void define(Setup setup, Topologies topologies) {
        setup.clusterPState("$$followeeToNotifiedFollowerIds", Relationships.class.getName(), "$$followeeToNotifiedFollowerIds");
        
    }

}