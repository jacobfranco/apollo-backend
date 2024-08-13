// src/com/apollo/backend/modules/ESports.java
package com.apollo.backend.modules;

import com.rpl.rama.*;
import com.rpl.rama.module.*;
import com.rpl.rama.ops.*;
import com.apollo.backend.ApolloHelpers;
import com.apollo.backend.data.Series;

public class ESports implements RamaModule {

    private static void declareSeriesIngestionTopology(Topologies topologies) {
        StreamTopology stream = topologies.stream("seriesIngestion");

        // Define PState for mapping seriesId to Series object
        stream.pstate("$$seriesIdToSeries", PState.mapSchema(Integer.class, Series.class));

        // Stream processing logic
        stream.source("*seriesDepot").out("*series")
                .macro(ApolloHelpers.extractFields("*series", "*id"))
                .localTransform("$$seriesIdToSeries", Path.key("*id").termVal("*series"));
    }

    private void declareQueries(Topologies topologies) {
        topologies.query("getSeriesFromSeriesId", "*id").out("*result")
            .hashPartition("*id")
            .localSelect("$$seriesIdToSeries", Path.key("*id").filterPred(Ops.IS_NOT_NULL))
            .out("*series")
            .ifTrue(new Expr(Ops.IS_NULL, "*series"),
                Block.each(() -> null).out("*result"),
                Block.each(Ops.IDENTITY, "*series").out("*result"))
            .originPartition();
    }

    @Override
    public void define(Setup setup, Topologies topologies) {
        // Define depots
        setup.declareDepot("*seriesDepot", Depot.hashBy(ApolloHelpers.ExtractSeriesId.class));

        // Define topology
        declareSeriesIngestionTopology(topologies);

        // Define queries
        declareQueries(topologies);
    }

}
