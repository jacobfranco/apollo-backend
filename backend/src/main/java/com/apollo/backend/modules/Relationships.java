package com.apollo.backend.modules;

import com.rpl.rama.*;

public class Relationships implements RamaModule {

  @Override
  public void define(Setup setup, Topologies topologies) {

      setup.declareDepot("*authCodeDepot", Depot.hashBy(ExtractCode.class));
  }

}