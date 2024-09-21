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
                                .localTransform("$$matchIdToMatch", Path.key("*id").termVal("*match"))
                                .hashPartition("*seriesId")
                                .compoundAgg("$$seriesIdToMatches",
                                                CompoundAgg.map("*seriesId", Agg.list("*match")));

        }

        private static void declareRosterIngestionTopology(Topologies topologies) {
                StreamTopology stream = topologies.stream("rosterIngestion");

                stream.pstate("$$rosterIdToRoster", PState.mapSchema(Integer.class, Roster.class));

                stream.source("*rosterDepot").out("*roster")
                                .macro(ApolloHelpers.extractFields("*roster", "*id"))
                                .localTransform("$$rosterIdToRoster", Path.key("*id").termVal("*roster"));
        }

        private static void declareTeamIngestionTopology(Topologies topologies) {
                StreamTopology stream = topologies.stream("teamIngestion");

                stream.pstate("$$teamIdToTeam", PState.mapSchema(Integer.class, Team.class));

                stream.source("*teamDepot").out("*team")
                                .macro(ApolloHelpers.extractFields("*team", "*id"))
                                .localTransform("$$teamIdToTeam", Path.key("*id").termVal("*team"));
        }

        private static void declarePlayerIngestionTopology(Topologies topologies) {
                StreamTopology stream = topologies.stream("playerIngestion");

                stream.pstate("$$playerIdToPlayer", PState.mapSchema(Integer.class, Player.class));

                stream.source("*playerDepot").out("*player")
                                .macro(ApolloHelpers.extractFields("*player", "*id"))
                                .localTransform("$$playerIdToPlayer", Path.key("*id").termVal("*player"));
        }

        private static void declareLolMatchSummaryIngestionTopology(Topologies topologies) {
                StreamTopology stream = topologies.stream("lolMatchSummaryIngestion");
                stream.pstate("$$lolMatchIdToSummary", PState.mapSchema(Integer.class, LolMatchSummary.class));
                stream.source("*lolMatchSummaryDepot").out("*summary")
                                .macro(ApolloHelpers.extractFields("*summary", "*id"))
                                .localTransform("$$lolMatchIdToSummary", Path.key("*id").termVal("*summary"));
        }

        private static void declareAssetIngestionTopology(Topologies topologies) {
                StreamTopology stream = topologies.stream("assetIngestion");

                stream.pstate("$$assetIdToAsset", PState.mapSchema(Integer.class, Asset.class));

                stream.source("*assetDepot").out("*asset")
                                .macro(ApolloHelpers.extractFields("*asset", "*id"))
                                .localTransform("$$assetIdToAsset", Path.key("*id").termVal("*asset"));
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
                                .hashPartition("*seriesId")
                                .localSelect("$$seriesIdToMatches", Path.key("*seriesId"))
                                .out("*matches")
                                .ifTrue(new Expr(Ops.IS_NULL, "*matches"),
                                                Block.each(() -> null).out("*result"),
                                                Block.each(Ops.IDENTITY, "*matches").out("*result"))
                                .originPartition();

                topologies.query("getRosterFromRosterId", "*id").out("*result")
                                .hashPartition("*id")
                                .localSelect("$$rosterIdToRoster", Path.key("*id"))
                                .out("*roster")
                                .ifTrue(new Expr(Ops.IS_NULL, "*roster"),
                                                Block.each(() -> null).out("*result"),
                                                Block.each(Ops.IDENTITY, "*roster").out("*result"))
                                .originPartition();

                topologies.query("getTeamFromTeamId", "*id").out("*result")
                                .hashPartition("*id")
                                .localSelect("$$teamIdToTeam", Path.key("*id"))
                                .out("*team")
                                .ifTrue(new Expr(Ops.IS_NULL, "*team"),
                                                Block.each(() -> null).out("*result"),
                                                Block.each(Ops.IDENTITY, "*team").out("*result"))
                                .originPartition();

                topologies.query("getPlayerFromPlayerId", "*id").out("*result")
                                .hashPartition("*id")
                                .localSelect("$$playerIdToPlayer", Path.key("*id"))
                                .out("*player")
                                .ifTrue(new Expr(Ops.IS_NULL, "*player"),
                                                Block.each(() -> null).out("*result"),
                                                Block.each(Ops.IDENTITY, "*player").out("*result"))
                                .originPartition();

                topologies.query("getLolMatchSummaryFromMatchId", "*id").out("*result")
                                .hashPartition("*id")
                                .localSelect("$$lolMatchIdToSummary", Path.key("*id"))
                                .out("*summary")
                                .ifTrue(new Expr(Ops.IS_NULL, "*summary"),
                                                Block.each(() -> null).out("*result"),
                                                Block.each(Ops.IDENTITY, "*summary").out("*result"))
                                .originPartition();

                topologies.query("getAssetFromAssetId", "*id").out("*result")
                                .hashPartition("*id")
                                .localSelect("$$assetIdToAsset", Path.key("*id"))
                                .out("*asset")
                                .ifTrue(new Expr(Ops.IS_NULL, "*asset"),
                                                Block.each(() -> null).out("*result"),
                                                Block.each(Ops.IDENTITY, "*asset").out("*result"))
                                .originPartition();

        }

        @Override
        public void define(Setup setup, Topologies topologies) {
                setup.declareDepot("*seriesDepot", Depot.hashBy(ApolloHelpers.ExtractSeriesId.class));
                setup.declareDepot("*matchDepot", Depot.hashBy(ApolloHelpers.ExtractMatchId.class));
                setup.declareDepot("*rosterDepot", Depot.hashBy(ApolloHelpers.ExtractRosterId.class));
                setup.declareDepot("*teamDepot", Depot.hashBy(ApolloHelpers.ExtractTeamId.class));
                setup.declareDepot("*playerDepot", Depot.hashBy(ApolloHelpers.ExtractPlayerId.class));
                setup.declareDepot("*lolMatchSummaryDepot", Depot.hashBy(ApolloHelpers.ExtractMatchId.class));
                setup.declareDepot("*assetDepot", Depot.hashBy(ApolloHelpers.ExtractAssetId.class));
                declareSeriesIngestionTopology(topologies);
                declareMatchIngestionTopology(topologies);
                declareRosterIngestionTopology(topologies);
                declareTeamIngestionTopology(topologies);
                declarePlayerIngestionTopology(topologies);
                declareLolMatchSummaryIngestionTopology(topologies);
                declareAssetIngestionTopology(topologies);
                declareQueries(topologies);
        }
}
