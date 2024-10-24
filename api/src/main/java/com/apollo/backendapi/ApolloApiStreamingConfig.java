package com.apollo.backendapi;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.apollo.backend.data.*;
import com.apollo.backendapi.pojos.*;
import com.rpl.rama.*;
import com.rpl.rama.diffs.*;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.context.annotation.*;
import org.springframework.core.Ordered;
import org.springframework.http.*;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.*;
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping;
import org.springframework.web.reactive.socket.*;
import org.springframework.web.reactive.socket.server.WebSocketService;
import org.springframework.web.reactive.socket.server.support.WebSocketHandlerAdapter;
import org.springframework.web.reactive.socket.server.upgrade.ReactorNettyRequestUpgradeStrategy;
import org.springframework.web.server.*;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.*;

import java.io.IOException;
import java.net.*;
import java.security.Principal;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Configuration
public class ApolloApiStreamingConfig {
    private static final String SEC_WEBSOCKET_PROTOCOL_HEADER = "Sec-WebSocket-Protocol";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private static final Logger logger = LogManager.getLogger(ApolloApiStreamingConfig.class);

    public static class StreamState {
        WebSocketSession session;
        Long accountId;
        FluxSink<WebSocketMessage> sink;
        String stream;
        List<ProxyState<SortedMap>> proxies;

        public StreamState(WebSocketSession session, Long accountId, FluxSink<WebSocketMessage> sink, String stream,
                List<ProxyState<SortedMap>> proxies) {
            this.session = session;
            this.accountId = accountId;
            this.sink = sink;
            this.stream = stream;
            this.proxies = proxies;
        }

        void close() throws IOException {
            for (ProxyState<SortedMap> proxy : this.proxies)
                proxy.close();
        }
    }

    // track the state for each connected streaming client
    public static final ConcurrentHashMap<String, StreamState> SESSION_ID_TO_STATE = new ConcurrentHashMap<>();

    private static String serializeEvent(StatusQueryResult statusQueryResult, String stream) {
        try {
            String statusStr = OBJECT_MAPPER.writeValueAsString(new GetStatus(statusQueryResult));
            GetStreamEvent event = new GetStreamEvent(stream, "update", statusStr);
            return OBJECT_MAPPER.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private static String serializeEvent(Conversation conversation, String stream) {
        try {
            String convoStr = OBJECT_MAPPER.writeValueAsString(new GetConversation(conversation));
            GetStreamEvent event = new GetStreamEvent(stream, "conversation", convoStr);
            return OBJECT_MAPPER.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private static String serializeEvent(GetNotification.Bundle notificationBundle, String stream) {
        try {
            String statusStr = OBJECT_MAPPER.writeValueAsString(new GetNotification(notificationBundle));
            GetStreamEvent event = new GetStreamEvent(stream, "notification", statusStr);
            return OBJECT_MAPPER.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private static String serializeEvent(GetLiveMatch getLiveMatch, String stream) {
        try {
            String liveMatchStr = OBJECT_MAPPER.writeValueAsString(getLiveMatch);
            GetStreamEvent event = new GetStreamEvent(stream, "liveMatch", liveMatchStr);
            return OBJECT_MAPPER.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            logger.error("Error serializing GetLiveMatch for matchId {}: {}",
                    getLiveMatch.id, e.getMessage(), e);
            return null;
        }
    }

    private static String serializeEvent(EditSeries editSeries, String stream) {
    try {
        String seriesStr = OBJECT_MAPPER.writeValueAsString(editSeries);
        GetStreamEvent event = new GetStreamEvent(stream, "series_update", seriesStr);
        return OBJECT_MAPPER.writeValueAsString(event);
    } catch (JsonProcessingException e) {
        logger.error("Error serializing EditSeries: {}", e.getMessage(), e);
        return null;
    }
}

private static String serializeEvent(EditMatch editMatch, String stream) {
    try {
        String matchStr = OBJECT_MAPPER.writeValueAsString(editMatch);
        GetStreamEvent event = new GetStreamEvent(stream, "match_update", matchStr);
        return OBJECT_MAPPER.writeValueAsString(event);
    } catch (JsonProcessingException e) {
        logger.error("Error serializing EditMatch: {}", e.getMessage(), e);
        return null;
    }
}

    public static void sendStatusPointer(WebSocketSession session, FluxSink<WebSocketMessage> sink, String stream,
            Long accountId, StatusPointer statusPointer) {
        // direct streams return a "conversation" event
        if ("direct".equals(stream))
            ApolloApiController.manager.getConversationFromStatusId(accountId, statusPointer)
                    .thenAccept(conversation -> {
                        if (conversation == null)
                            return;
                        String eventStr = serializeEvent(conversation, stream);
                        if (eventStr != null)
                            sink.next(session.textMessage(eventStr));
                    });
        // other streams return an "update" event
        else {
            final FilterContext context;
            if ("user".equals(stream))
                context = FilterContext.Home;
            else
                context = FilterContext.Public;
            QueryFilterOptions filterOptions = new QueryFilterOptions(context, true);
            ApolloApiController.manager.getStatus(accountId, statusPointer, filterOptions)
                    .thenAccept(statusQueryResult -> {
                        sendStatusQueryResult(session, sink, stream, statusQueryResult);
                    });
        }
    }

    public static void sendStatusQueryResult(WebSocketSession session, FluxSink<WebSocketMessage> sink, String stream,
            StatusQueryResult statusQueryResult) {
        if (statusQueryResult == null)
            return;
        String eventStr = serializeEvent(statusQueryResult, stream);
        if (eventStr != null)
            sink.next(session.textMessage(eventStr));
    }

    public static void sendNotificationWithId(WebSocketSession session, FluxSink<WebSocketMessage> sink, String stream,
            long accountId, NotificationWithId nwid) {
        ApolloApiController.manager.getNotification(accountId, nwid)
                .thenAccept(bundle -> {
                    if (bundle == null)
                        return;
                    String eventStr = serializeEvent(bundle, stream);
                    if (eventStr != null)
                        sink.next(session.textMessage(eventStr));
                });
    }

    // **New Method to Send Live Match Updates**
    public static void sendLiveMatch(WebSocketSession session, FluxSink<WebSocketMessage> sink, String stream,
            GetLiveMatch getLiveMatch) {
        String eventStr = serializeEvent(getLiveMatch, stream);
        if (eventStr != null) {
            // Log the serialized event before sending
            logger.info("Sending liveMatch event to session {}: {}", session.getId(), eventStr);

            sink.next(session.textMessage(eventStr));
        } else {
            logger.warn("Failed to serialize GetLiveMatch for session {}", session.getId());
        }
    }

    public static void sendSeriesUpdate(EditSeries editSeries) {
    String stream = "series_updates"; // Define a stream identifier for series updates
    String eventStr = serializeEvent(editSeries, stream);
    if (eventStr != null) {
        SESSION_ID_TO_STATE.forEach((sessionId, streamState) -> {
            if (streamState.stream.equals(stream)) {
                streamState.sink.next(streamState.session.textMessage(eventStr));
            }
        });
    }
}

// Method to send match update to subscribed clients
public static void sendMatchUpdate(EditMatch editMatch) {
    String stream = "match_updates"; // Define a stream identifier for match updates
    String eventStr = serializeEvent(editMatch, stream);
    if (eventStr != null) {
        SESSION_ID_TO_STATE.forEach((sessionId, streamState) -> {
            if (streamState.stream.equals(stream)) {
                streamState.sink.next(streamState.session.textMessage(eventStr));
            }
        });
    }
}

    // caches of the latest query results of each global timeline
    public static final ConcurrentHashMap<LocalTimeline, ConcurrentSkipListMap<Long, StatusQueryResult>> LOCAL_TIMELINE_TO_INDEX_TO_STATUS = new ConcurrentHashMap() {
        {
            put(LocalTimeline.Public, new ConcurrentSkipListMap<>());
        }
    };
    public static final ConcurrentHashMap<LocalTimeline, ConcurrentHashMap<StatusPointer, Long>> LOCAL_TIMELINE_TO_STATUS_POINTER_TO_INDEX = new ConcurrentHashMap() {
        {
            put(LocalTimeline.Public, new ConcurrentHashMap<>());
        }
    };
    public static final int GLOBAL_TIMELINE_CACHE_SIZE = 4000; // how many statuses to keep in memory for each timeline
    public static final int GLOBAL_TIMELINE_QUERY_LIMIT = 10; // how many statuses to query on each iteration

    public static class StatusPointerDiffProcessor implements Diff.Processor, KeyDiff.Processor {
        public List<StatusPointer> statusPointers = new ArrayList<>();

        @Override
        public void processKeyDiff(KeyDiff diff) {
            NewValueDiff newValueDiff = (NewValueDiff) diff.getValDiff();
            Object val = newValueDiff.getVal();
            StatusPointer statusPointer = (StatusPointer) val;
            statusPointers.add(statusPointer);
        }

        @Override
        public void unhandled() {
            statusPointers = null;
        }
    }

    public static class NotificationWithIdDiffProcessor implements Diff.Processor, KeyDiff.Processor {
        public List<NotificationWithId> nwids = new ArrayList<>();

        @Override
        public void processKeyDiff(KeyDiff diff) {
            long notificationId = (long) diff.getKey();
            NewValueDiff newValueDiff = (NewValueDiff) diff.getValDiff();
            Notification notification = (Notification) newValueDiff.getVal();
            nwids.add(new NotificationWithId(notificationId, notification));
        }

        @Override
        public void unhandled() {
            nwids = null;
        }
    }

    @Bean
    public WebSocketHandler webSocketHandler() {
        return new WebSocketHandler() {
            @Override
            public Mono<Void> handle(WebSocketSession session) {
                final String wsSessionId = session.getId();
                final Long accountId = (Long) session.getAttributes().get("accountId");

                MultiValueMap<String, String> params = UriComponentsBuilder.fromUri(session.getHandshakeInfo().getUri())
                        .build().getQueryParams();

                return session.send(Flux.create(sink -> {
                    if (SESSION_ID_TO_STATE.containsKey(wsSessionId))
                        return;
                    if (!params.containsKey("stream"))
                        return;
                    String stream = params.get("stream").get(0);

                    // TODO: Change this so that its uniform i.e. get rid of local/remote
                    if ("public".equals(stream) || "public:local".equals(stream) || "public:remote".equals(stream)) {
    SESSION_ID_TO_STATE.put(wsSessionId,
            new StreamState(session, accountId, sink, stream, new ArrayList<>()));
} else if (stream.startsWith("live-match") || stream.startsWith("series_updates") || stream.startsWith("match_updates")) { 
    SESSION_ID_TO_STATE.put(wsSessionId,
            new StreamState(session, accountId, sink, stream, new ArrayList<>()));
    // **Note:** Live match summaries, series updates, and match updates will be pushed manually via ApolloApiManager
}
 else {
                        ProxyState.Callback<SortedMap> statusCallback = (SortedMap newVal, Diff diff,
                                SortedMap oldVal) -> {
                            StatusPointerDiffProcessor processor = new StatusPointerDiffProcessor();
                            diff.process(processor);
                            // no diff was provided (rare)
                            if (processor.statusPointers == null) {
                                if (newVal != null && oldVal != null) {
                                    for (Object key : newVal.keySet()) {
                                        long timelineIndex = (long) key;
                                        if (!oldVal.containsKey(timelineIndex)) {
                                            StatusPointer statusPointer = (StatusPointer) newVal.get(timelineIndex);
                                            sendStatusPointer(session, sink, stream, accountId, statusPointer);
                                        }
                                    }
                                }
                            } else {
                                for (StatusPointer statusPointer : processor.statusPointers)
                                    sendStatusPointer(session, sink, stream, accountId, statusPointer);
                            }
                        };

                        if ("hashtag".equals(stream)) {
                            if (!params.containsKey("tag")) {
                                sink.complete();
                                return;
                            }
                            String tag = params.get("tag").get(0);
                            ApolloApiController.manager
                                    .proxyHashtagTimeline(tag, statusCallback)
                                    .thenAccept(proxy -> SESSION_ID_TO_STATE.put(wsSessionId,
                                            new StreamState(session, accountId, sink, stream, Arrays.asList(proxy))));
                        } else if ("user".equals(stream)) {
                            if (accountId == null)
                                return; // login required
                            ProxyState.Callback<SortedMap> notificationCallback = (SortedMap newVal, Diff diff,
                                    SortedMap oldVal) -> {
                                NotificationWithIdDiffProcessor processor = new NotificationWithIdDiffProcessor();
                                diff.process(processor);
                                // no diff was provided (rare)
                                if (processor.nwids == null) {
                                    if (newVal != null && oldVal != null) {
                                        for (Object key : newVal.keySet()) {
                                            long notificationId = (long) key;
                                            if (!oldVal.containsKey(notificationId)) {
                                                Notification notification = (Notification) newVal.get(notificationId);
                                                sendNotificationWithId(session, sink, stream, accountId,
                                                        new NotificationWithId(notificationId, notification));
                                            }
                                        }
                                    }
                                } else {
                                    for (NotificationWithId nwid : processor.nwids)
                                        sendNotificationWithId(session, sink, stream, accountId, nwid);
                                }
                            };
                            // make reactive queries for home and notifications timelines
                            ApolloApiController.manager
                                    .proxyHomeTimeline(accountId, statusCallback)
                                    .thenCompose(homeProxy -> {
                                        // temporarily save home proxy by itself so it will be properly closed
                                        // if the notifications proxy is not successfully created.
                                        SESSION_ID_TO_STATE.put(wsSessionId, new StreamState(session, accountId, sink,
                                                stream, Arrays.asList(homeProxy)));
                                        // make reactive query for notifications timeline
                                        return ApolloApiController.manager
                                                .proxyNotificationsTimeline(accountId, notificationCallback)
                                                .thenAccept(notificationsProxy -> SESSION_ID_TO_STATE.put(wsSessionId,
                                                        new StreamState(session, accountId, sink, stream,
                                                                Arrays.asList(homeProxy, notificationsProxy))));
                                    });
                        } else if ("direct".equals(stream)) {
                            if (accountId == null)
                                return; // login required
                            ApolloApiController.manager
                                    .proxyDirectTimeline(accountId, statusCallback)
                                    .thenAccept(proxy -> SESSION_ID_TO_STATE.put(wsSessionId,
                                            new StreamState(session, accountId, sink, stream, Arrays.asList(proxy))));
                        }
                    }
                }))
                        .and(session.receive()
                                .doFinally(sig -> {
                                    session.close();
                                    StreamState state = SESSION_ID_TO_STATE.remove(wsSessionId);
                                    if (state != null) {
                                        try {
                                            state.close();
                                        } catch (IOException e) {
                                            throw new RuntimeException(e);
                                        }
                                    }
                                }).then());
            }
        };
    }

    @Bean
    public HandlerMapping webSocketHandlerMapping() {
        Map<String, WebSocketHandler> map = new HashMap<>();
        WebSocketHandler handler = webSocketHandler();
        map.put("/api/streaming/", handler);
        map.put("/api/streaming", handler);

        SimpleUrlHandlerMapping handlerMapping = new SimpleUrlHandlerMapping();
        handlerMapping.setOrder(Ordered.HIGHEST_PRECEDENCE);
        handlerMapping.setUrlMap(map);
        return handlerMapping;
    }

    @Bean
    public WebSocketHandlerAdapter handlerAdapter() {
        return new WebSocketHandlerAdapter(webSocketService());
    }

    @Bean
    public WebSocketService webSocketService() {
        return new WebSocketService() {
            @Override
            public Mono<Void> handleRequest(ServerWebExchange exchange, WebSocketHandler handler) {
                ServerHttpRequest request = exchange.getRequest();
                HttpMethod method = request.getMethod();
                HttpHeaders headers = request.getHeaders();

                if (HttpMethod.GET != method) {
                    return Mono.error(new MethodNotAllowedException(request.getMethodValue(),
                            Collections.singleton(HttpMethod.GET)));
                }

                if (!"WebSocket".equalsIgnoreCase(headers.getUpgrade())) {
                    return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Invalid 'Upgrade' header: " + headers));
                }

                List<String> connectionValue = headers.getConnection();
                if (!connectionValue.contains("Upgrade") && !connectionValue.contains("upgrade")) {
                    return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Invalid 'Connection' header: " + headers));
                }

                String protocol = headers.getFirst(SEC_WEBSOCKET_PROTOCOL_HEADER);

                return initAttributes(exchange).flatMap(
                        attributes -> new ReactorNettyRequestUpgradeStrategy().upgrade(exchange, handler, protocol,
                                () -> createHandshakeInfo(exchange, request, protocol, attributes)));
            }

            private Mono<Map<String, Object>> initAttributes(ServerWebExchange exchange) {
                return exchange.getSession().map(session -> session.getAttributes().entrySet().stream()
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
            }

            private HandshakeInfo createHandshakeInfo(ServerWebExchange exchange, ServerHttpRequest request,
                    String protocol, Map<String, Object> attributes) {

                URI uri = request.getURI();
                // Copy request headers, as they might be pooled and recycled by
                // the server implementation once the handshake HTTP exchange is done.
                HttpHeaders headers = new HttpHeaders();
                headers.addAll(request.getHeaders());
                MultiValueMap<String, org.springframework.http.HttpCookie> cookies = request.getCookies();
                Mono<Principal> principal = exchange.getPrincipal();
                String logPrefix = exchange.getLogPrefix();
                InetSocketAddress remoteAddress = request.getRemoteAddress();
                return new HandshakeInfo(uri, headers, cookies, principal, protocol, remoteAddress, attributes,
                        logPrefix);
            }
        };
    }
}