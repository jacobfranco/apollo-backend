package com.apollo.backend.modules;

import com.rpl.rama.*;
import com.rpl.rama.module.*;
import com.rpl.rama.ops.*;
import com.apollo.backend.ApolloHelpers;
import com.apollo.backend.data.*;

public class ESports implements RamaModule {

        private static void declareSeriesIngestionTopology(Topologies topologies) {
                StreamTopology stream = topologies.stream("seriesIngestion");

                stream.pstate("$$startTimeToSeries",
                                PState.mapSchema(Long.class, PState.mapSchema(Integer.class, Series.class)));

                stream.source("*seriesDepot").out("*series")
                                .macro(ApolloHelpers.extractFields("*series", "*id", "*start"))
                                .hashPartition("*id")
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

        private static void declareTournamentIngestionTopology(Topologies topologies) {
                StreamTopology stream = topologies.stream("tournamentIngestion");

                stream.pstate("$$tournamentIdToTournament", PState.mapSchema(Integer.class, Tournament.class));

                stream.source("*tournamentDepot").out("*tournament")
                                .macro(ApolloHelpers.extractFields("*tournament", "*id"))
                                .localTransform("$$tournamentIdToTournament", Path.key("*id").termVal("*tournament"));
        }

        private static void declareSubstageIngestionTopology(Topologies topologies) {
                StreamTopology stream = topologies.stream("substageIngestion");

                stream.pstate("$$substageIdToSubstage", PState.mapSchema(Integer.class, Substage.class));

                stream.source("*substageDepot").out("*substage")
                                .macro(ApolloHelpers.extractFields("*substage", "*id"))
                                .localTransform("$$substageIdToSubstage", Path.key("*id").termVal("*substage"));
        }

        private static void declareCasterIngestionTopology(Topologies topologies) {
                StreamTopology stream = topologies.stream("casterIngestion");

                stream.pstate("$$casterIdToCaster", PState.mapSchema(Integer.class, Caster.class));

                stream.source("*casterDepot").out("*caster")
                                .macro(ApolloHelpers.extractFields("*caster", "*id"))
                                .localTransform("$$casterIdToCaster", Path.key("*id").termVal("*caster"));
        }

        private static void declareLolMatchSummaryIngestionTopology(Topologies topologies) {
                StreamTopology stream = topologies.stream("lolMatchSummaryIngestion");
                stream.pstate("$$lolMatchIdToSummary", PState.mapSchema(Integer.class, LolMatchSummary.class));
                stream.pstate("$$lolMatchIdToAssetIds",
                                PState.mapSchema(Integer.class, PState.setSchema(Integer.class)));

                stream.source("*lolMatchSummaryDepot").out("*summary")
                                .macro(ApolloHelpers.extractFields("*summary", "*id", "*assetIds"))
                                .localTransform("$$lolMatchIdToSummary", Path.key("*id").termVal("*summary"))
                                .localTransform("$$lolMatchIdToAssetIds", Path.key("*id").termVal("*assetIds"));
        }

        private static void declareLolPlayerSeasonStatsIngestionTopology(Topologies topologies) {
                StreamTopology stream = topologies.stream("lolPlayerSeasonStatsIngestion");
                stream.pstate("$$playerIdToLolSeasonStats",
                                PState.mapSchema(Integer.class, PState.listSchema(LolPlayerSummary.class)));

                stream.source("*lolPlayerSeasonStatsDepot").out("*playerSummary")
                                .macro(ApolloHelpers.extractFields("*playerSummary", "*id"))
                                .compoundAgg("$$playerIdToLolSeasonStats",
                                                CompoundAgg.map("*id", Agg.list("*playerSummary")));
        }

        private static void declareLolTeamSeasonStatsIngestionTopology(Topologies topologies) {
                StreamTopology stream = topologies.stream("lolTeamSeasonStatsIngestion");
                stream.pstate("$$teamIdToLolSeasonStats",
                                PState.mapSchema(Integer.class, PState.listSchema(LolTeamSummary.class)));
                stream.source("*lolTeamSeasonStatsDepot").out("*teamSummary")
                                .macro(ApolloHelpers.extractFields("*teamSummary", "*teamId"))
                                .compoundAgg("$$teamIdToLolSeasonStats",
                                                CompoundAgg.map("*teamId", Agg.list("*teamSummary")));
        }

        private static void declareAssetIngestionTopology(Topologies topologies) {
                StreamTopology stream = topologies.stream("assetIngestion");
                stream.pstate("$$assetIdToAsset", PState.mapSchema(Integer.class, Asset.class));
                stream.pstate("$$gameIdToAssets", PState.mapSchema(Integer.class, PState.setSchema(Asset.class)));

                stream.source("*assetDepot").out("*asset")
                                .macro(ApolloHelpers.extractFields("*asset", "*id", "*game.id"))
                                .localTransform("$$assetIdToAsset", Path.key("*id").termVal("*asset"))
                                .compoundAgg("$$gameIdToAssets",
                                                CompoundAgg.map("*game_id", Agg.set("*asset")));
        }

        private static void declareLiveLolMatchSummaryIngestionTopology(Topologies topologies) {
                StreamTopology stream = topologies.stream("liveLolMatchSummaryIngestion");

                // Define PState map schema with matchId as the key and LiveLolMatchSummary as
                // the value
                stream.pstate("$$liveLolMatchIdToSummary", PState.mapSchema(Integer.class, LiveLolMatchSummary.class));

                // Source from the liveLolMatchSummaryDepot
                stream.source("*liveLolMatchSummaryDepot").out("*liveSummary")
                                .macro(ApolloHelpers.extractFields("*liveSummary", "*matchId"))
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

                topologies.query("getAssetIdsFromMatchId", "*id").out("*result")
                                .hashPartition("*id")
                                .localSelect("$$lolMatchIdToAssetIds", Path.key("*id")).out("*result")
                                .originPartition();

                topologies.query("getAssetsFromGameId", "*gameId").out("*result")
                                .hashPartition("*gameId")
                                .localSelect("$$gameIdToAssets", Path.key("*gameId")).out("*result")
                                .originPartition();

                topologies.query("getLiveLolMatchSummaryFromMatchId", "*matchId").out("*result")
                                .hashPartition("*matchId")
                                .localSelect("$$liveLolMatchIdToSummary", Path.key("*matchId")).out("*result")
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
                setup.declareDepot("*tournamentDepot", Depot.hashBy(ApolloHelpers.ExtractTournamentId.class));
                setup.declareDepot("*substageDepot", Depot.hashBy(ApolloHelpers.ExtractSubstageId.class));
                setup.declareDepot("*casterDepot", Depot.hashBy(ApolloHelpers.ExtractCasterId.class));
                setup.declareDepot("*lolPlayerSeasonStatsDepot", Depot.hashBy(ApolloHelpers.ExtractPlayerId.class));
                setup.declareDepot("*lolTeamSeasonStatsDepot", Depot.hashBy(ApolloHelpers.ExtractLolTeamId.class));
                setup.declareDepot("*liveLolMatchSummaryDepot", Depot.hashBy(ApolloHelpers.ExtractLiveMatchId.class));
                declareSeriesIngestionTopology(topologies);
                declareMatchIngestionTopology(topologies);
                declareRosterIngestionTopology(topologies);
                declareTeamIngestionTopology(topologies);
                declarePlayerIngestionTopology(topologies);
                declareLolMatchSummaryIngestionTopology(topologies);
                declareAssetIngestionTopology(topologies);
                declareTournamentIngestionTopology(topologies);
                declareSubstageIngestionTopology(topologies);
                declareCasterIngestionTopology(topologies);
                declareLolPlayerSeasonStatsIngestionTopology(topologies);
                declareLolTeamSeasonStatsIngestionTopology(topologies);
                declareLiveLolMatchSummaryIngestionTopology(topologies);
                declareQueries(topologies);
        }
}
