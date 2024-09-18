package com.apollo.backend.modules;

import com.rpl.rama.*;
import com.rpl.rama.module.*;
import com.rpl.rama.ops.*;
import com.apollo.backend.ApolloHelpers;
import com.apollo.backend.data.*;

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

        private static void declareMatchIngestionTopology(Topologies topologies) {
                StreamTopology stream = topologies.stream("matchIngestion");

                stream.pstate("$$matchIdToMatch", PState.mapSchema(Integer.class, Match.class));
                stream.pstate("$$seriesIdToMatches",
                                PState.mapSchema(Integer.class, PState.listSchema(Match.class)));

                stream.source("*matchDepot").out("*match")
                                .macro(ApolloHelpers.extractFields("*match", "*id", "*seriesId"))
                                .each(Ops.PRINTLN, "Ingesting Match:", "*id", "SeriesId:", "*seriesId")
                                .localTransform("$$matchIdToMatch", Path.key("*id").termVal("*match"))
                                .each(Ops.PRINTLN, "Match added to $$matchIdToMatch:", "*id", "->", "*match")
                                .hashPartition("*seriesId")
                                .compoundAgg("$$seriesIdToMatches",
                                                CompoundAgg.map("*seriesId", Agg.list("*match")))
                                .each(Ops.PRINTLN, "Match added to $$seriesIdToMatches:", "*seriesId", "*match");

        }

        private static void declareRosterIngestionTopology(Topologies topologies) {
                StreamTopology stream = topologies.stream("rosterIngestion");

                stream.pstate("$$rosterIdToRoster", PState.mapSchema(Integer.class, Roster.class));

                stream.source("*rosterDepot").out("*roster")
                                .macro(ApolloHelpers.extractFields("*roster", "*id"))
                                .each(Ops.PRINTLN, "Ingesting Roster:", "*id")
                                .localTransform("$$rosterIdToRoster", Path.key("*id").termVal("*roster"))
                                .each(Ops.PRINTLN, "Roster added to $$rosterIdToRoster:", "*id", "*roster");
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

                topologies.query("getMatchFromMatchId", "*id").out("*result")
                                .hashPartition("*id")
                                .localSelect("$$matchIdToMatch", Path.key("*id"))
                                .out("*match")
                                .ifTrue(new Expr(Ops.IS_NULL, "*match"),
                                                Block.each(() -> null).out("*result"),
                                                Block.each(Ops.IDENTITY, "*match").out("*result"))
                                .originPartition();

                topologies.query("getMatchesFromSeriesId", "*seriesId").out("*result")
                                .each(Ops.PRINTLN, "Getting matches from series id: ", "*seriesId")
                                .hashPartition("*seriesId")
                                .localSelect("$$seriesIdToMatches", Path.key("*seriesId"))
                                .out("*matches")
                                .each(Ops.PRINTLN, "Matches found for series:", "*seriesId", "Matches:", "*matches")
                                .ifTrue(new Expr(Ops.IS_NULL, "*matches"),
                                                Block.each(() -> null).out("*result"),
                                                Block.each(Ops.IDENTITY, "*matches").out("*result"))
                                .each(Ops.PRINTLN, "Retrieved matches for series:", "*seriesId", "Result:", "*result")
                                .originPartition();

                topologies.query("getRosterFromRosterId", "*id").out("*result")
                                .hashPartition("*id")
                                .localSelect("$$rosterIdToRoster", Path.key("*id"))
                                .out("*roster")
                                .ifTrue(new Expr(Ops.IS_NULL, "*roster"),
                                                Block.each(() -> null).out("*result"),
                                                Block.each(Ops.IDENTITY, "*roster").out("*result"))
                                .originPartition();

        }

        @Override
        public void define(Setup setup, Topologies topologies) {
                setup.declareDepot("*seriesDepot", Depot.hashBy(ApolloHelpers.ExtractSeriesId.class));
                setup.declareDepot("*matchDepot", Depot.hashBy(ApolloHelpers.ExtractMatchId.class));
                setup.declareDepot("*rosterDepot", Depot.hashBy(ApolloHelpers.ExtractRosterId.class));
                declareSeriesIngestionTopology(topologies);
                declareMatchIngestionTopology(topologies);
                declareRosterIngestionTopology(topologies);
                declareQueries(topologies);
        }
}
