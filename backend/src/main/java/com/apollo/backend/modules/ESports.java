package com.apollo.backend.modules;

import com.rpl.rama.*;
import com.rpl.rama.module.*;
import com.rpl.rama.ops.*;
import com.apollo.backend.ApolloHelpers;
import com.apollo.backend.data.Series;
import static com.apollo.backend.ApolloHelpers.extractFields;

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
        
        // Correctly named PStates
        stream.pstate("$$idToSeries", PState.mapSchema(Long.class, Series.class));
        stream.pstate("$$tournamentToSeriesIds", PState.mapSchema(Long.class, PState.setSchema(Long.class)));
        stream.pstate("$$gameToSeriesIds", PState.mapSchema(Long.class, PState.setSchema(Long.class)));
        stream.pstate("$$startTimeToUpcomingSeriesIds", PState.mapSchema(Long.class, PState.setSchema(Long.class)));
        stream.pstate("$$idToOngoingSeriesId", PState.mapSchema(Long.class, Long.class));
        stream.pstate("$$endTimeToCompletedSeriesIds", PState.mapSchema(Long.class, PState.setSchema(Long.class)));

        stream.source("*seriesDepot").out("*data")
            .macro(extractFields("*data", "*id", "*tournamentId", "*gameId", "*status", "*startTime", "*endTime"))
            .localTransform("$$idToSeries", Path.key("*id").termVal("*data"))
            .localTransform("$$tournamentToSeriesIds", Path.key("*tournamentId").termVal("*id"))
            .localTransform("$$gameToSeriesIds", Path.key("*gameId").termVal("*id"))
            .ifTrue(new Expr(Ops.EQUAL, "*status", "upcoming"),
                Block.localTransform("$$startTimeToUpcomingSeriesIds", Path.key("*startTime").termVal("*id")))
            .ifTrue(new Expr(Ops.EQUAL, "*status", "ongoing"),
                Block.localTransform("$$idToOngoingSeriesId", Path.key("*id").termVal("*id")))
            .ifTrue(new Expr(Ops.EQUAL, "*status", "completed"),
                Block.localTransform("$$endTimeToCompletedSeriesIds", Path.key("*endTime").termVal("*id")));
    }
}