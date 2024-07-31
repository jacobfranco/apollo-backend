package com.apollo.backend.modules;

import com.apollo.backend.ApolloHelpers;
import com.apollo.backend.data.Series;
import com.rpl.rama.*;
import com.rpl.rama.module.StreamTopology;

public class ESports implements RamaModule {

     private void declareSeriesTopology(Topologies topologies) {
    // TODO
}

    @Override
    public void define(Setup setup, Topologies topologies) {

        setup.declareDepot("*seriesDepot", Depot.hashBy(ApolloHelpers.ExtractSeriesId.class));
      
        setup.clusterPState("$$seriesIdToSeries", ESports.class.getName(), "$$seriesIdToSeries");

        declareSeriesTopology(topologies);

    }


   
}