package com.apollo.backend.modules;

import com.apollo.backend.ApolloHelpers;
import com.rpl.rama.*;

public class Core implements RamaModule {

  @Override
  public void define(Setup setup, Topologies topologies) {

    setup.declareDepot("*accountDepot", Depot.hashBy(ApolloHelpers.ExtractName.class));
      
  }

}