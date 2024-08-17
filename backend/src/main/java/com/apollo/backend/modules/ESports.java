package com.apollo.backend.modules;

import com.rpl.rama.*;
import com.rpl.rama.module.*;
import com.rpl.rama.ops.*;
import com.apollo.backend.ApolloHelpers;
import com.apollo.backend.data.Series;

public class ESports implements RamaModule {

    private static void declareSeriesIngestionTopology(Topologies topologies) {
        StreamTopology stream = topologies.stream("seriesIngestion");

        stream.pstate("$$seriesIdToSeries", PState.mapSchema(Integer.class, Series.class));
        stream.pstate("$$startTimeToSeries",
                PState.mapSchema(Long.class, PState.mapSchema(Integer.class, Series.class)));

        stream.source("*seriesDepot").out("*series")
              .macro(ApolloHelpers.extractFields("*series", "*id", "*start"))
              .localTransform("$$seriesIdToSeries", 
                  Path.key("*id").termVal("*series"))
              .localTransform("$$startTimeToSeries",
                  Path.key("*start").key("*id").termVal("*series"));
    }

    private void declareQueries(Topologies topologies) {
        topologies.query("getSeriesFromSeriesId", "*id").out("*result")
            .hashPartition("*id")
            .localSelect("$$seriesIdToSeries", Path.key("*id"))
            .out("*series")
            .ifTrue(new Expr(Ops.IS_NULL, "*series"),
                    Block.each(() -> null).out("*result"),
                    Block.each(Ops.IDENTITY, "*series").out("*result"))
            .originPartition();

            topologies.query("getSeriesSchedule", "*startTime", "*endTime").out("*result")
            .hashPartition("*startTime")
            .localSelect("$$startTimeToSeries", Path.sortedMapRange("*startTime", "*endTime"))
            .out("*result")
            .originPartition();
    }

    @Override
    public void define(Setup setup, Topologies topologies) {
        setup.declareDepot("*seriesDepot", Depot.hashBy(ApolloHelpers.ExtractSeriesId.class));
        declareSeriesIngestionTopology(topologies);
        declareQueries(topologies);
    }
}