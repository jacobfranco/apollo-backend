// src/com/apollo/backend/modules/ESports.java
package com.apollo.backend.modules;

import com.rpl.rama.*;
import com.rpl.rama.module.*;
import com.rpl.rama.ops.*;
import com.apollo.backend.ApolloHelpers;
import com.apollo.backend.data.Series;

public class ESports implements RamaModule {

    @Override
    public void define(Setup setup, Topologies topologies) {
        // Define depots
        setup.declareDepot("*seriesDepot", Depot.hashBy(ApolloHelpers.ExtractSeriesId.class));

        // Define topology
        declareSeriesIngestionTopology(topologies);
    }

    private static void declareSeriesIngestionTopology(Topologies topologies) {
        StreamTopology stream = topologies.stream("seriesIngestion");

        // Define PState for mapping seriesId to Series object
        stream.pstate("$$seriesIdToSeries", PState.mapSchema(Integer.class, Series.class));

        // Stream processing logic
        stream.source("*seriesDepot").out("*series")
            .macro(ApolloHelpers.extractFields("*series", "*id"))
            .localTransform("$$seriesIdToSeries", Path.key("*id").termVal("*series"));
    }
}
