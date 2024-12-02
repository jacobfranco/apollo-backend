package com.apollo.backend.modules;

import com.rpl.rama.*;
import com.rpl.rama.module.*;
import com.apollo.backend.*;
import com.apollo.backend.data.*;

import static com.apollo.backend.ApolloHelpers.extractFields;

public class ESports implements RamaModule {

        private static void declareSeriesTopology(Topologies topologies) {
                StreamTopology stream = topologies.stream("series");

                stream.pstate("$$seriesIdToSeries", PState.mapSchema(Integer.class, Series.class));

                stream.pstate("$$startTimeToSeries",
                                PState.mapSchema(Long.class, PState.mapSchema(Integer.class, Series.class)));

                stream.source("*seriesDepot").out("*series")
                                .macro(extractFields("*series", "*id", "*start"))
                                .localTransform("$$seriesIdToSeries", Path.key("*id").termVal("*series"))
                                .localTransform("$$startTimeToSeries",
                                                Path.key("*start").key("*id").termVal("*series"));
        }

        private static void declareMatchTopology(Topologies topologies) {
                StreamTopology stream = topologies.stream("match");

                stream.pstate("$$matchIdToMatch", PState.mapSchema(Integer.class, Match.class));

                stream.source("*matchDepot").out("*match")
                                .macro(extractFields("*match", "*id", "*seriesId"))
                                .localTransform("$$matchIdToMatch", Path.key("*id").termVal("*match"));

        }

        private static void declareRosterTopology(Topologies topologies) {
                StreamTopology stream = topologies.stream("roster");

                stream.pstate("$$rosterIdToRoster", PState.mapSchema(Integer.class, Roster.class));

                stream.source("*rosterDepot").out("*roster")
                                .macro(extractFields("*roster", "*id"))
                                .localTransform("$$rosterIdToRoster", Path.key("*id").termVal("*roster"));
        }

        private static void declareTeamTopology(Topologies topologies) {
                StreamTopology stream = topologies.stream("team");

                stream.pstate("$$teamIdToTeam", PState.mapSchema(Integer.class, Team.class));

                stream.source("*teamDepot").out("*team")
                                .macro(extractFields("*team", "*id"))
                                .localTransform("$$teamIdToTeam", Path.key("*id").termVal("*team"));
        }

        private static void declareScheduleTopology(Topologies topologies) {
                StreamTopology stream = topologies.stream("schedule");

                stream.pstate("$$teamIdToSeriesIds", PState.mapSchema(Integer.class, PState.listSchema(Integer.class)));

                stream.source("*scheduleDepot").out("*schedule")
                                .macro(extractFields("*schedule", "*id", "*seriesIds"))
                                .localTransform("$$teamIdToSeriesIds", Path.key("*id").termVal("*seriesIds"));
        }

        private static void declarePlayerTopology(Topologies topologies) {
                StreamTopology stream = topologies.stream("player");

                stream.pstate("$$playerIdToPlayer", PState.mapSchema(Integer.class, Player.class));

                stream.source("*playerDepot").out("*player")
                                .macro(extractFields("*player", "*id"))
                                .localTransform("$$playerIdToPlayer", Path.key("*id").termVal("*player"));
        }

        private static void declareTournamentTopology(Topologies topologies) {
                StreamTopology stream = topologies.stream("tournament");

                stream.pstate("$$tournamentIdToTournament", PState.mapSchema(Integer.class, Tournament.class));

                stream.source("*tournamentDepot").out("*tournament")
                                .macro(extractFields("*tournament", "*id"))
                                .localTransform("$$tournamentIdToTournament", Path.key("*id").termVal("*tournament"));
        }

        private static void declareSubstageTopology(Topologies topologies) {
                StreamTopology stream = topologies.stream("substage");

                stream.pstate("$$substageIdToSubstage", PState.mapSchema(Integer.class, Substage.class));

                stream.source("*substageDepot").out("*substage")
                                .macro(extractFields("*substage", "*id"))
                                .localTransform("$$substageIdToSubstage", Path.key("*id").termVal("*substage"));
        }

        private static void declareCasterTopology(Topologies topologies) {
                StreamTopology stream = topologies.stream("caster");

                stream.pstate("$$casterIdToCaster", PState.mapSchema(Integer.class, Caster.class));

                stream.source("*casterDepot").out("*caster")
                                .macro(extractFields("*caster", "*id"))
                                .localTransform("$$casterIdToCaster", Path.key("*id").termVal("*caster"));
        }

        private static void declareLolMatchSummaryTopology(Topologies topologies) {
                StreamTopology stream = topologies.stream("lolMatchSummary");
                stream.pstate("$$lolMatchIdToSummary", PState.mapSchema(Integer.class, LolMatchSummary.class));

                stream.source("*lolMatchSummaryDepot").out("*summary")
                                .macro(extractFields("*summary", "*id", "*assetIds"))
                                .localTransform("$$lolMatchIdToSummary", Path.key("*id").termVal("*summary"));
        }

        private static void declareLolPlayerSeasonStatsTopology(Topologies topologies) {
                StreamTopology stream = topologies.stream("lolPlayerSeasonStats");
                stream.pstate("$$playerIdToLolSeasonStats",
                                PState.mapSchema(Integer.class, PState.listSchema(LolPlayerSummary.class)));

                stream.source("*lolPlayerSeasonStatsDepot").out("*playerSummary")
                                .macro(extractFields("*playerSummary", "*id"))
                                .compoundAgg("$$playerIdToLolSeasonStats",
                                                CompoundAgg.map("*id", Agg.list("*playerSummary")));
        }

        private static void declareLolTeamSeasonStatsTopology(Topologies topologies) {
                StreamTopology stream = topologies.stream("lolTeamSeasonStats");
                stream.pstate("$$teamIdToLolSeasonStats",
                                PState.mapSchema(Integer.class, PState.listSchema(LolTeamSummary.class)));
                stream.source("*lolTeamSeasonStatsDepot").out("*teamSummary")
                                .macro(extractFields("*teamSummary", "*teamId"))
                                .compoundAgg("$$teamIdToLolSeasonStats",
                                                CompoundAgg.map("*teamId", Agg.list("*teamSummary")));
        }

        private static void declareAssetTopology(Topologies topologies) {
                StreamTopology stream = topologies.stream("asset");
                stream.pstate("$$assetIdToAsset", PState.mapSchema(Integer.class, Asset.class));

                stream.source("*assetDepot").out("*asset")
                                .macro(extractFields("*asset", "*id"))
                                .localTransform("$$assetIdToAsset", Path.key("*id").termVal("*asset"));
        }

        private static void declareLiveLolMatchSummaryTopology(Topologies topologies) {
                StreamTopology stream = topologies.stream("liveLolMatchSummary");

                // Define PState map schema with matchId as the key and LiveLolMatchSummary as
                // the value
                stream.pstate("$$liveLolMatchIdToSummary", PState.mapSchema(Integer.class, LiveLolMatchSummary.class));

                // Source from the liveLolMatchSummaryDepot
                stream.source("*liveLolMatchSummaryDepot").out("*liveSummary")
                                .macro(extractFields("*liveSummary", "*matchId"))
                                .localTransform("$$liveLolMatchIdToSummary",
                                                Path.key("*matchId").termVal("*liveSummary"));
        }

        private static void declareLolTeamAggStatsTopology(Topologies topologies) {
                StreamTopology stream = topologies.stream("lolTeamAggStats");
                stream.pstate("$$teamIdToLolTeamAggStats", PState.mapSchema(Integer.class, LolTeamAggStats.class));

                stream.source("*lolTeamAggStatsDepot").out("*aggStats")
                                .macro(extractFields("*aggStats", "*id"))
                                .localTransform("$$teamIdToLolTeamAggStats", Path.key("*id").termVal("*aggStats"));
        }

        private static void declareLolPlayerAggStatsTopology(Topologies topologies) {
                StreamTopology stream = topologies.stream("lolPlayerAggStats");
                stream.pstate("$$playerIdToLolPlayerAggStats",
                                PState.mapSchema(Integer.class, LolPlayerAggStats.class));

                stream.source("*lolPlayerAggStatsDepot").out("*aggStats")
                                .macro(extractFields("*aggStats", "*id"))
                                .localTransform("$$playerIdToLolPlayerAggStats", Path.key("*id").termVal("*aggStats"));
        }

        private void declareQueries(Topologies topologies) {
                topologies.query("getSeriesFromSeriesId", "*id").out("*result")
                                .hashPartition("*id")
                                .localSelect("$$seriesIdToSeries", Path.key("*id")).out("*result")
                                .originPartition();

                topologies.query("getSeriesFromStartTime", "*startTime", "*endTime").out("*result")
                                .allPartition()
                                .localSelect("$$startTimeToSeries", Path.sortedMapRange("*startTime", "*endTime"))
                                .out("*seriesMap")
                                .originPartition()
                                .agg(Agg.mergeMap("*seriesMap")).out("*result");

                topologies.query("getMatchFromMatchId", "*id").out("*result")
                                .hashPartition("*id")
                                .localSelect("$$matchIdToMatch", Path.key("*id")).out("*result")
                                .originPartition();

                topologies.query("getRosterFromRosterId", "*id").out("*result")
                                .hashPartition("*id")
                                .localSelect("$$rosterIdToRoster", Path.key("*id")).out("*result")
                                .originPartition();

                topologies.query("getTeamFromTeamId", "*id").out("*result")
                                .hashPartition("*id")
                                .localSelect("$$teamIdToTeam", Path.key("*id")).out("*result")
                                .originPartition();

                topologies.query("getSeriesIdsFromTeamId", "*id").out("*result")
                                .hashPartition("*id")
                                .localSelect("$$teamIdToSeriesIds", Path.key("*id")).out("*result")
                                .originPartition();

                topologies.query("getAllTeamIds").out("*result")
                                .allPartition()
                                .localSelect("$$teamIdToTeam", Path.mapKeys())
                                .out("*ids")
                                .originPartition()
                                .agg(Agg.list("*ids")).out("*result");

                topologies.query("getPlayerFromPlayerId", "*id").out("*result")
                                .hashPartition("*id")
                                .localSelect("$$playerIdToPlayer", Path.key("*id")).out("*result")
                                .originPartition();

                topologies.query("getAllPlayerIds").out("*result")
                                .allPartition()
                                .localSelect("$$playerIdToPlayer", Path.mapKeys())
                                .out("*ids")
                                .originPartition()
                                .agg(Agg.list("*ids")).out("*result");

                topologies.query("getLolMatchSummaryFromMatchId", "*id").out("*result")
                                .hashPartition("*id")
                                .localSelect("$$lolMatchIdToSummary", Path.key("*id")).out("*result")
                                .originPartition();

                topologies.query("getLolPlayerSeasonStatsFromPlayerId", "*id").out("*result")
                                .hashPartition("*id")
                                .localSelect("$$playerIdToLolSeasonStats", Path.key("*id")).out("*result")
                                .originPartition();

                topologies.query("getLolTeamSeasonStatsFromTeamId", "*id").out("*result")
                                .hashPartition("*id")
                                .localSelect("$$teamIdToLolSeasonStats", Path.key("*id")).out("*result")
                                .originPartition();

                topologies.query("getAssetFromAssetId", "*id").out("*result")
                                .hashPartition("*id")
                                .localSelect("$$assetIdToAsset", Path.key("*id")).out("*result")
                                .originPartition();

                topologies.query("getTournamentFromTournamentId", "*id").out("*result")
                                .hashPartition("*id")
                                .localSelect("$$tournamentIdToTournament", Path.key("*id")).out("*result")
                                .originPartition();

                topologies.query("getSubstageFromSubstageId", "*id").out("*result")
                                .hashPartition("*id")
                                .localSelect("$$substageIdToSubstage", Path.key("*id")).out("*result")
                                .originPartition();

                topologies.query("getCasterFromCasterId", "*id").out("*result")
                                .hashPartition("*id")
                                .localSelect("$$casterIdToCaster", Path.key("*id")).out("*result")
                                .originPartition();

                topologies.query("getLiveLolMatchSummaryFromMatchId", "*matchId").out("*result")
                                .hashPartition("*matchId")
                                .localSelect("$$liveLolMatchIdToSummary", Path.key("*matchId")).out("*result")
                                .originPartition();

                topologies.query("getLolTeamAggStatsFromTeamId", "*id").out("*result")
                                .hashPartition("*id")
                                .localSelect("$$teamIdToLolTeamAggStats", Path.key("*id")).out("*result")
                                .originPartition();

                topologies.query("getLolPlayerAggStatsFromPlayerId", "*id").out("*result")
                                .hashPartition("*id")
                                .localSelect("$$playerIdToLolPlayerAggStats", Path.key("*id")).out("*result")
                                .originPartition();

        }

        @Override
        public void define(Setup setup, Topologies topologies) {
                setup.declareDepot("*seriesDepot", Depot.hashBy(ApolloHelpers.ExtractSeriesId.class));
                setup.declareDepot("*matchDepot", Depot.hashBy(ApolloHelpers.ExtractMatchId.class));
                setup.declareDepot("*rosterDepot", Depot.hashBy(ApolloHelpers.ExtractRosterId.class));
                setup.declareDepot("*teamDepot", Depot.hashBy(ApolloHelpers.ExtractTeamId.class));
                setup.declareDepot("*scheduleDepot", Depot.hashBy(ApolloHelpers.ExtractTeamId.class));
                setup.declareDepot("*playerDepot", Depot.hashBy(ApolloHelpers.ExtractPlayerId.class));
                setup.declareDepot("*lolMatchSummaryDepot", Depot.hashBy(ApolloHelpers.ExtractMatchId.class));
                setup.declareDepot("*assetDepot", Depot.hashBy(ApolloHelpers.ExtractAssetId.class));
                setup.declareDepot("*tournamentDepot", Depot.hashBy(ApolloHelpers.ExtractTournamentId.class));
                setup.declareDepot("*substageDepot", Depot.hashBy(ApolloHelpers.ExtractSubstageId.class));
                setup.declareDepot("*casterDepot", Depot.hashBy(ApolloHelpers.ExtractCasterId.class));
                setup.declareDepot("*lolPlayerSeasonStatsDepot", Depot.hashBy(ApolloHelpers.ExtractPlayerId.class));
                setup.declareDepot("*lolTeamSeasonStatsDepot", Depot.hashBy(ApolloHelpers.ExtractLolTeamId.class));
                setup.declareDepot("*liveLolMatchSummaryDepot", Depot.hashBy(ApolloHelpers.ExtractLiveMatchId.class));
                setup.declareDepot("*lolTeamAggStatsDepot", Depot.hashBy(ApolloHelpers.ExtractTeamId.class));
                setup.declareDepot("*lolPlayerAggStatsDepot", Depot.hashBy(ApolloHelpers.ExtractPlayerId.class));
                declareSeriesTopology(topologies);
                declareMatchTopology(topologies);
                declareRosterTopology(topologies);
                declareTeamTopology(topologies);
                declareScheduleTopology(topologies);
                declarePlayerTopology(topologies);
                declareLolMatchSummaryTopology(topologies);
                declareAssetTopology(topologies);
                declareTournamentTopology(topologies);
                declareSubstageTopology(topologies);
                declareCasterTopology(topologies);
                declareLolPlayerSeasonStatsTopology(topologies);
                declareLolTeamSeasonStatsTopology(topologies);
                declareLiveLolMatchSummaryTopology(topologies);
                declareLolTeamAggStatsTopology(topologies);
                declareLolPlayerAggStatsTopology(topologies);
                declareQueries(topologies);
        }
}
