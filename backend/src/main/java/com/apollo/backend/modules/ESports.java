package com.apollo.backend.modules;

import com.rpl.rama.*;
import com.rpl.rama.module.*;
import com.rpl.rama.ops.*;
import com.apollo.backend.*;
import com.apollo.backend.data.*;
import com.apollo.backend.navs.*;

import static com.apollo.backend.ApolloHelpers.extractFields;

public class ESports implements RamaModule {

        private static void declareSeriesTopology(Topologies topologies) {
                StreamTopology stream = topologies.stream("series");

                stream.pstate("$$seriesIdToSeries", PState.mapSchema(Integer.class, Series.class));

                stream.pstate("$$startTimeToSeries",
                                PState.mapSchema(Long.class, PState.mapSchema(Integer.class, Series.class)));

                stream.source("*seriesDepot").out("*series")
                                .macro(extractFields("*series", "*id", "*start"))
                                .hashPartition("*id")
                                .localTransform("$$startTimeToSeries",
                                                Path.key("*start").key("*id").termVal("*series"));

                stream.source("*seriesEditDepot", StreamSourceOptions.retryNone()).out("*editSeries")
                                .macro(extractFields("*editSeries", "*id", "*edits"))
                                .each(Ops.EXPLODE, "*edits").out("*edit")
                                .each((EditSeriesField editSeriesField, OutputCollector collector) -> {
                                        collector.emit(editSeriesField.getSetField().getFieldName(),
                                                        editSeriesField.getFieldValue());
                                }, "*edit").out("*fieldName", "*fieldValue")
                                .localTransform("$$seriesIdToSeries", Path.must("*id")
                                                .customNavBuilder(TField::new, "*fieldName")
                                                .termVal("*fieldValue"));
        }

        private static void declareMatchTopology(Topologies topologies) {
                StreamTopology stream = topologies.stream("match");

                stream.pstate("$$matchIdToMatch", PState.mapSchema(Integer.class, Match.class));
                stream.pstate("$$seriesIdToMatches",
                                PState.mapSchema(Integer.class, PState.listSchema(Match.class)));

                stream.source("*matchDepot").out("*match")
                                .macro(extractFields("*match", "*id", "*seriesId"))
                                .localTransform("$$matchIdToMatch", Path.key("*id").termVal("*match"))
                                .hashPartition("*seriesId")
                                .compoundAgg("$$seriesIdToMatches",
                                                CompoundAgg.map("*seriesId", Agg.list("*match")));

                stream.source("*matchEditDepot", StreamSourceOptions.retryNone()).out("*editMatch")
                                .macro(extractFields("*editMatch", "*id", "*edits"))
                                .each(Ops.EXPLODE, "*edits").out("*edit")
                                .each((EditMatchField editMatchField, OutputCollector collector) -> {
                                        collector.emit(editMatchField.getSetField().getFieldName(),
                                                        editMatchField.getFieldValue());
                                }, "*edit").out("*fieldName", "*fieldValue")
                                .localTransform("$$matchIdToMatch", Path.must("*id")
                                                .customNavBuilder(TField::new, "*fieldName")
                                                .termVal("*fieldValue"));

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

        private void declareQueries(Topologies topologies) {
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

                topologies.query("getMatchesFromSeriesId", "*seriesId").out("*result")
                                .hashPartition("*seriesId")
                                .localSelect("$$seriesIdToMatches", Path.key("*seriesId")).out("*result")
                                .originPartition();

                topologies.query("getRosterFromRosterId", "*id").out("*result")
                                .hashPartition("*id")
                                .localSelect("$$rosterIdToRoster", Path.key("*id")).out("*result")
                                .originPartition();

                topologies.query("getTeamFromTeamId", "*id").out("*result")
                                .hashPartition("*id")
                                .localSelect("$$teamIdToTeam", Path.key("*id")).out("*result")
                                .originPartition();

                topologies.query("getPlayerFromPlayerId", "*id").out("*result")
                                .hashPartition("*id")
                                .localSelect("$$playerIdToPlayer", Path.key("*id")).out("*result")
                                .originPartition();

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
        }

        @Override
        public void define(Setup setup, Topologies topologies) {
                setup.declareDepot("*seriesDepot", Depot.hashBy(ApolloHelpers.ExtractSeriesId.class));
                setup.declareDepot("*seriesEditDepot", Depot.hashBy(ApolloHelpers.ExtractSeriesId.class));
                setup.declareDepot("*matchDepot", Depot.hashBy(ApolloHelpers.ExtractMatchId.class));
                setup.declareDepot("*matchEditDepot", Depot.hashBy(ApolloHelpers.ExtractMatchId.class));
                setup.declareDepot("*rosterDepot", Depot.hashBy(ApolloHelpers.ExtractRosterId.class));
                setup.declareDepot("*teamDepot", Depot.hashBy(ApolloHelpers.ExtractTeamId.class));
                setup.declareDepot("*playerDepot", Depot.hashBy(ApolloHelpers.ExtractPlayerId.class));
                setup.declareDepot("*lolMatchSummaryDepot", Depot.hashBy(ApolloHelpers.ExtractMatchId.class));
                setup.declareDepot("*assetDepot", Depot.hashBy(ApolloHelpers.ExtractAssetId.class));
                setup.declareDepot("*tournamentDepot", Depot.hashBy(ApolloHelpers.ExtractTournamentId.class));
                setup.declareDepot("*substageDepot", Depot.hashBy(ApolloHelpers.ExtractSubstageId.class));
                setup.declareDepot("*casterDepot", Depot.hashBy(ApolloHelpers.ExtractCasterId.class));
                setup.declareDepot("*lolPlayerSeasonStatsDepot", Depot.hashBy(ApolloHelpers.ExtractPlayerId.class));
                setup.declareDepot("*lolTeamSeasonStatsDepot", Depot.hashBy(ApolloHelpers.ExtractLolTeamId.class));
                setup.declareDepot("*liveLolMatchSummaryDepot", Depot.hashBy(ApolloHelpers.ExtractLiveMatchId.class));
                declareSeriesTopology(topologies);
                declareMatchTopology(topologies);
                declareRosterTopology(topologies);
                declareTeamTopology(topologies);
                declarePlayerTopology(topologies);
                declareLolMatchSummaryTopology(topologies);
                declareAssetTopology(topologies);
                declareTournamentTopology(topologies);
                declareSubstageTopology(topologies);
                declareCasterTopology(topologies);
                declareLolPlayerSeasonStatsTopology(topologies);
                declareLolTeamSeasonStatsTopology(topologies);
                declareLiveLolMatchSummaryTopology(topologies);
                declareQueries(topologies);
        }
}
