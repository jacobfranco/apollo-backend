package com.apollo.backendapi.pojos;

import com.apollo.backend.data.LolPlayerSummary;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class GetLolPlayerSummary {
        public int id;
        public int uiIndex;
        public GetLolChampion champion;
        public GetLolKills kills;
        public GetLolAssists assists;
        public GetLolDeaths deaths;
        public GetLolRevives revives;
        public List<GetLolMultiKill> multiKills;
        public List<Integer> killStreaks;
        public GetLolItems items;
        public List<GetLolSummonerSpell> summonerSpells;
        public GetLolCreeps creeps;
        public GetLolKeystone keystone;
        public GetLolPosition position;
        public Long start;
        public Integer matchId;
        public GetTeam opponent;

        public GetLolPlayerSummary(LolPlayerSummary playerSummary, Map<Integer, GetAsset> assetMap) {
                this.id = playerSummary.getId();
                this.uiIndex = playerSummary.getUiIndex();

                // Map champion correctly
                this.champion = playerSummary.getChampion() != null
                                ? new GetLolChampion(playerSummary.getChampion(), assetMap)
                                : null;

                this.kills = new GetLolKills(playerSummary.getKills());
                this.assists = new GetLolAssists(playerSummary.getAssists());
                this.deaths = new GetLolDeaths(playerSummary.getDeaths());
                this.revives = new GetLolRevives(playerSummary.getRevives());

                this.multiKills = Optional.ofNullable(playerSummary.getMultiKills())
                                .orElse(Collections.emptyList())
                                .stream()
                                .map(GetLolMultiKill::new)
                                .collect(Collectors.toList());

                this.killStreaks = playerSummary.getKillStreaks();

                // Map items correctly
                this.items = playerSummary.getItems() != null ? new GetLolItems(playerSummary.getItems(), assetMap)
                                : null;

                // Map summoner spells correctly
                this.summonerSpells = playerSummary.getSummonerSpells() != null
                                ? playerSummary.getSummonerSpells().stream()
                                                .map(spell -> new GetLolSummonerSpell(spell, assetMap))
                                                .collect(Collectors.toList())
                                : Collections.emptyList();

                // Map creeps
                this.creeps = playerSummary.getCreeps() != null ? new GetLolCreeps(playerSummary.getCreeps(), assetMap)
                                : null;

                // Map keystone correctly
                this.keystone = playerSummary.isSetKeystone()
                                ? new GetLolKeystone(playerSummary.getKeystone(), assetMap)
                                : null;

                // Map position
                this.position = playerSummary.isSetPosition() ? new GetLolPosition(playerSummary.getPosition()) : null;

                // Handle start and matchId if set
                if (playerSummary.isSetStart()) {
                        this.start = playerSummary.getStart(); // epoch millis
                } else {
                        this.start = null;
                }

                if (playerSummary.isSetMatchId()) {
                        this.matchId = playerSummary.getMatchId();
                } else {
                        this.matchId = null;
                }

                // Opponent
                if (playerSummary.isSetOpponent()) {
                        // Map opponent team
                        this.opponent = new GetTeam(playerSummary.getOpponent());
                } else {
                        this.opponent = null;
                }
        }
}
