package com.apollo.backendapi.pojos;

import com.apollo.backend.data.LolPlayerSummary;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.time.Instant;

public class GetLolPlayerMatchStats {
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
        public Instant matchStart;
        public int matchId;
        public boolean isWinner;
        public int score;
        public int goldEarned;
        public int turretsDestroyed;
        public int inhibitorsDestroyed;
        public GetLolFaction faction;
        public GetLolStructures structures;

        /**
         * Constructor that initializes GetLolPlayerMatchStats from GetLolPlayerSummary.
         * Initializes default values if the teamSummary is null.
         *
         * @param playerSummary The GetLolPlayerSummary object.
         */
        public GetLolPlayerMatchStats(GetLolPlayerSummary playerSummary) {
                if (playerSummary == null) {
                        // Initialize default values
                        this.id = 0;
                        this.uiIndex = 0;
                        this.champion = null;
                        this.kills = null;
                        this.assists = null;
                        this.deaths = null;
                        this.revives = null;
                        this.multiKills = Collections.emptyList();
                        this.killStreaks = Collections.emptyList();
                        this.items = null;
                        this.summonerSpells = Collections.emptyList();
                        this.creeps = null;
                        this.keystone = null;
                        this.position = null;
                        this.matchStart = null;
                        this.matchId = 0;
                        this.isWinner = false;
                        this.score = 0;
                        this.goldEarned = 0;
                        this.turretsDestroyed = 0;
                        this.inhibitorsDestroyed = 0;
                        this.faction = null;
                        this.structures = null;
                        return;
                }

                this.id = playerSummary.id;
                this.uiIndex = playerSummary.uiIndex;
                this.champion = playerSummary.champion;
                this.kills = playerSummary.kills;
                this.assists = playerSummary.assists;
                this.deaths = playerSummary.deaths;
                this.revives = playerSummary.revives;
                this.multiKills = playerSummary.multiKills;
                this.killStreaks = playerSummary.killStreaks;
                this.items = playerSummary.items;
                this.summonerSpells = playerSummary.summonerSpells;
                this.creeps = playerSummary.creeps;
                this.keystone = playerSummary.keystone;
                this.position = playerSummary.position;

                // Assuming these fields are part of GetLolPlayerSummary or need to be set
                // separately
                // If they are not part of GetLolPlayerSummary, you might need to adjust
                // accordingly
                this.matchStart = null; // Set appropriately if available
                this.matchId = 0; // Set appropriately if available
                this.isWinner = false; // Set appropriately if available
                this.score = 0; // Set appropriately if available
                this.goldEarned = 0; // Set appropriately if available
                this.turretsDestroyed = 0; // Set appropriately if available
                this.inhibitorsDestroyed = 0; // Set appropriately if available
                this.faction = null; // Set appropriately if available
                this.structures = null; // Set appropriately if available
        }

        /**
         * Constructor that initializes GetLolPlayerMatchStats from LolPlayerSummary and
         * assetMap.
         * Initializes default values if the teamSummary is null.
         *
         * @param playerSummary The LolPlayerSummary object.
         * @param assetMap      The asset map for mapping assets.
         */
        public GetLolPlayerMatchStats(LolPlayerSummary playerSummary, Map<Integer, GetAsset> assetMap) {
                if (playerSummary == null) {
                        // Initialize default values
                        this.id = 0;
                        this.uiIndex = 0;
                        this.champion = null;
                        this.kills = null;
                        this.assists = null;
                        this.deaths = null;
                        this.revives = null;
                        this.multiKills = Collections.emptyList();
                        this.killStreaks = Collections.emptyList();
                        this.items = null;
                        this.summonerSpells = Collections.emptyList();
                        this.creeps = null;
                        this.keystone = null;
                        this.position = null;
                        this.matchStart = null;
                        this.matchId = 0;
                        this.isWinner = false;
                        this.score = 0;
                        this.goldEarned = 0;
                        this.turretsDestroyed = 0;
                        this.inhibitorsDestroyed = 0;
                        this.faction = null;
                        this.structures = null;
                        return;
                }

                // Initialize fields based on LolPlayerSummary
                this.id = playerSummary.getId();
                this.uiIndex = playerSummary.getUiIndex();
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

                this.items = playerSummary.getItems() != null
                                ? new GetLolItems(playerSummary.getItems(), assetMap)
                                : null;

                this.summonerSpells = playerSummary.getSummonerSpells() != null
                                ? playerSummary.getSummonerSpells().stream()
                                                .map(spell -> new GetLolSummonerSpell(spell, assetMap))
                                                .collect(Collectors.toList())
                                : Collections.emptyList();

                this.creeps = playerSummary.getCreeps() != null
                                ? new GetLolCreeps(playerSummary.getCreeps(), assetMap)
                                : null;

                this.keystone = playerSummary.isSetKeystone()
                                ? new GetLolKeystone(playerSummary.getKeystone(), assetMap)
                                : null;

                this.position = playerSummary.isSetPosition()
                                ? new GetLolPosition(playerSummary.getPosition())
                                : null;
        }

}
