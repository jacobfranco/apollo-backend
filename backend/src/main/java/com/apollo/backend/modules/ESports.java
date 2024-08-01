package com.apollo.backend.modules;

import com.rpl.rama.*;
import com.rpl.rama.module.*;
import com.rpl.rama.ops.*;
import com.apollo.backend.data.*;
import org.asynchttpclient.*;

import com.apollo.backendapi.AbiosApiClient;

import java.util.List;
import java.util.Map;
import java.io.IOException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;

import java.util.stream.Collectors;

public class ESports implements RamaModule {

    public static final String MODULE_NAME = ESports.class.getName();

    public static class AbiosApiClientTaskGlobal implements TaskGlobalObject {
        public AbiosApiClient apiClient;

        @Override
        public void prepareForTask(int taskId, TaskGlobalContext context) {
            AsyncHttpClient httpClient = Dsl.asyncHttpClient();
            String apiKey = System.getenv("ABIOS_API_KEY");
            apiClient = new AbiosApiClient(httpClient, apiKey);
        }

        @Override
        public void close() throws IOException {
            // Close any resources if needed
        }
    }

    @Override
    public void define(Setup setup, Topologies topologies) {
        setup.declareDepot("*seriesDepot", Depot.hashBy(Ops.IDENTITY));
        setup.declareObject("*abiosApiClient", new AbiosApiClientTaskGlobal());

        StreamTopology s = topologies.stream("fetchSeries");
        s.pstate("$$seriesIdToSeries", PState.mapSchema(Integer.class, Series.class));

        s.source("*seriesDepot").out("*params")
         .eachAsync((AbiosApiClientTaskGlobal client, Map<String, String> params) ->
                 client.apiClient.getSeries(params),
             "*abiosApiClient", "*params").out("*response")
         .each(ESports::parseAndConvertToThrift, "*response").out("*seriesList")
         .each(series -> {
             Map<Integer, Series> seriesMap = series.stream()
                 .collect(Collectors.toMap(Series::getId, s -> s));
             return seriesMap;
         }, "*seriesList").out("*seriesMap")
         .localTransform("$$seriesIdToSeries", Ops.MERGE);
    }

    private static List<Series> parseAndConvertToThrift(String jsonResponse) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        List<Map<String, Object>> jsonList = mapper.readValue(jsonResponse, new TypeReference<List<Map<String, Object>>>(){});
        
        return jsonList.stream().map(ESports::convertToSeries).collect(Collectors.toList());
    }

    private static Series convertToSeries(Map<String, Object> json) {
        Series series = new Series();
        series.setId((Integer) json.get("id"));
        series.setTitle((String) json.get("title"));
        series.setStart((String) json.get("start"));
        series.setEnd((String) json.get("end"));
        series.setLifecycle((String) json.get("lifecycle"));
        series.setTier((Integer) json.get("tier"));
        series.setBest_of((Integer) json.get("best_of"));
        
        List<Integer> chainIds = ((List<Map<String, Object>>) json.get("chain")).stream()
            .map(chain -> (Integer) chain.get("id"))
            .collect(Collectors.toList());
        series.setChain_ids(chainIds);

        series.setStreamed((Boolean) json.get("streamed"));
        
        Map<String, Object> bracketPos = (Map<String, Object>) json.get("bracket_position");
        if (bracketPos != null) {
            BracketPosition bp = new BracketPosition(
                (String) bracketPos.get("part"),
                (Integer) bracketPos.get("col"),
                (Integer) bracketPos.get("offset")
            );
            series.setBracket_position(bp);
        }

        series.setTournament_id((Integer) ((Map<String, Object>) json.get("tournament")).get("id"));
        series.setSubstage_id((Integer) ((Map<String, Object>) json.get("substage")).get("id"));
        series.setGame_id((Integer) ((Map<String, Object>) json.get("game")).get("id"));

        Map<String, Object> formatMap = (Map<String, Object>) json.get("format");
        Format format = new Format((Integer) formatMap.get("best_of"));
        series.setFormat(format);

        series.setPostponed_from((String) json.get("postponed_from"));
        series.setDeleted_at((String) json.get("deleted_at"));

        List<Participant> participants = ((List<Map<String, Object>>) json.get("participants")).stream()
            .map(p -> new Participant(
                (Integer) p.get("seed"),
                (Integer) p.get("score"),
                (Boolean) p.get("forfeit"),
                (Integer) ((Map<String, Object>) p.get("roster")).get("id"),
                (Boolean) p.get("winner"),
                new ParticipantStats(
                    (Integer) ((Map<String, Object>) p.get("stats")).get("kills"),
                    (Integer) ((Map<String, Object>) p.get("stats")).get("placement")
                )
            )).collect(Collectors.toList());
        series.setParticipants(participants);

        List<Integer> matchIds = ((List<Map<String, Object>>) json.get("matches")).stream()
            .map(m -> (Integer) m.get("id"))
            .collect(Collectors.toList());
        series.setMatch_ids(matchIds);

        List<Caster> casters = ((List<Map<String, Object>>) json.get("casters")).stream()
            .map(c -> new Caster(
                (Boolean) c.get("primary"),
                (Integer) ((Map<String, Object>) c.get("caster")).get("id")
            )).collect(Collectors.toList());
        series.setCasters(casters);

        List<Broadcaster> broadcasters = ((List<Map<String, Object>>) json.get("broadcasters")).stream()
            .map(b -> {
                Map<String, Object> broadcaster = (Map<String, Object>) b.get("broadcaster");
                List<Broadcast> broadcasts = ((List<Map<String, Object>>) b.get("broadcasts")).stream()
                    .map(br -> new Broadcast(
                        (String) br.get("external_id"),
                        (Integer) ((Map<String, Object>) br.get("language")).get("id")
                    )).collect(Collectors.toList());
                return new Broadcaster(
                    (Integer) broadcaster.get("id"),
                    (String) broadcaster.get("name"),
                    (String) broadcaster.get("external_id"),
                    (Integer) ((Map<String, Object>) broadcaster.get("platform")).get("id"),
                    (Integer) ((Map<String, Object>) ((Map<String, Object>) broadcaster.get("broadcast_defaults")).get("language")).get("id"),
                    broadcasts,
                    (Boolean) b.get("official")
                );
            }).collect(Collectors.toList());
        series.setBroadcasters(broadcasters);

        series.setHas_incident_report((Boolean) json.get("has_incident_report"));

        Map<String, Object> gameVersion = (Map<String, Object>) json.get("game_version");
        Map<String, Object> release = (Map<String, Object>) gameVersion.get("release");
        series.setGame_version(new GameVersion(
            (String) release.get("uuid"),
            (String) release.get("date"),
            (String) release.get("description")
        ));

        Map<String, Object> coverage = (Map<String, Object>) json.get("coverage");
        Map<String, Object> data = (Map<String, Object>) coverage.get("data");
        series.setCoverage(new Coverage(
            convertCoverageData((Map<String, Object>) ((Map<String, Object>) data.get("live")).get("api")),
            convertCoverageData((Map<String, Object>) ((Map<String, Object>) data.get("live")).get("cv")),
            convertCoverageData((Map<String, Object>) ((Map<String, Object>) data.get("realtime")).get("api")),
            convertCoverageData((Map<String, Object>) ((Map<String, Object>) data.get("realtime")).get("server")),
            convertCoverageData((Map<String, Object>) ((Map<String, Object>) data.get("postgame")).get("api")),
            convertCoverageData((Map<String, Object>) ((Map<String, Object>) data.get("postgame")).get("server"))
        ));

        series.setResource_version((Integer) json.get("resource_version"));

        return series;
    }

    private static CoverageData convertCoverageData(Map<String, Object> data) {
        return new CoverageData(
            (String) data.get("expectation"),
            (String) data.get("fact")
        );
    }

    public static void fetchSeriesOnStartup(Depot seriesDepot) {
        Map<String, String> params = Map.of(
            "filter", "lifecycle=upcoming",
            "order", "start-asc",
            "skip", "0",
            "take", "100"
        );
        seriesDepot.append(params);
    }
}