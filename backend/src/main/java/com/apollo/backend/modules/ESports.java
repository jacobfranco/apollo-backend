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

        // Define PStates
        setup.clusterPState("$$seriesById", ESports.class.getName(), "$$seriesById");
        setup.clusterPState("$$seriesByTournament", ESports.class.getName(), "$$seriesByTournament");
        setup.clusterPState("$$seriesByGame", ESports.class.getName(), "$$seriesByGame");
        setup.clusterPState("$$upcomingSeries", ESports.class.getName(), "$$upcomingSeries");
        setup.clusterPState("$$ongoingSeries", ESports.class.getName(), "$$ongoingSeries");
        setup.clusterPState("$$completedSeries", ESports.class.getName(), "$$completedSeries");

        // Define topology
        declareSeriesIngestionTopology(topologies);
    }

    private static void declareSeriesIngestionTopology(Topologies topologies) {
        StreamTopology stream = topologies.stream("seriesIngestion");

        stream.pstate("$$seriesById", PState.mapSchema(Long.class, Series.class));
        stream.pstate("$$seriesByTournament", PState.mapSchema(Long.class, PState.setSchema(Long.class)));
        stream.pstate("$$seriesByGame", PState.mapSchema(Long.class, PState.setSchema(Long.class)));
        stream.pstate("$$upcomingSeries", PState.mapSchema(Long.class, PState.setSchema(Long.class)));
        stream.pstate("$$ongoingSeries", PState.mapSchema(Long.class, Long.class));
        stream.pstate("$$completedSeries", PState.mapSchema(Long.class, PState.setSchema(Long.class)));

        stream.source("*seriesDepot").out("*data")
            .macro(extractFields("*data", "*id", "*tournamentId", "*gameId", "*status", "*startTime", "*endTime"))
            .localTransform("$$seriesById", Path.key("*id").termVal("*data"))
            .localTransform("$$seriesByTournament", Path.key("*tournamentId").termVal("*id"))
            .localTransform("$$seriesByGame", Path.key("*gameId").termVal("*id"))
            .ifTrue(new Expr(Ops.EQUAL, "*status", "upcoming"),
                Block.localTransform("$$upcomingSeries", Path.key("*startTime").termVal("*id")))
            .ifTrue(new Expr(Ops.EQUAL, "*status", "ongoing"),
                Block.localTransform("$$ongoingSeries", Path.key("*id").termVal("*id")))
            .ifTrue(new Expr(Ops.EQUAL, "*status", "completed"),
                Block.localTransform("$$completedSeries", Path.key("*endTime").termVal("*id")));
    }
}