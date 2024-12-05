package com.apollo.backendapi;

import com.google.common.collect.Lists;

import clojure.lang.PersistentHashMap;
import clojure.lang.PersistentVector;

import java.io.*;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.threeten.extra.PeriodDuration;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.AbstractMap.SimpleEntry;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import io.github.cdimascio.dotenv.Dotenv;
import rpl.rama.util.vector_backed_structures.VectorBackedSortedMap;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;

import com.apollo.backend.*;
import com.apollo.backend.data.*;
import com.apollo.backend.modules.*;
import com.apollo.backendapi.ApolloApiManager.QueryResults;
import com.apollo.backendapi.pojos.*;
import com.apollo.shared.ApolloSpaces;
import com.rpl.rama.*;
import com.rpl.rama.cluster.ClusterManagerBase;
import com.rpl.rama.ops.Ops;
import com.rpl.rama.diffs.*;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ApolloApiManager {

    private ObjectMapper objectMapper;

    private static final Logger logger = LogManager.getLogger(ApolloApiManager.class);

    // Load environment variables for Abios
    private static final Dotenv dotenv = Dotenv.load();
    private static final String ABIOS_SECRET = dotenv.get("ABIOS_SECRET");
    private static final AwsCredentialsProvider credentialsProvider;

    private final AbiosApiClient apiClient;

    private Map<Integer, Roster> rosterCache = new ConcurrentHashMap<>();

    // Abios Rate Limit Constants
    private final AtomicInteger remainingRequests = new AtomicInteger(Integer.MAX_VALUE);
    private final AtomicLong resetTime = new AtomicLong(0);
    private final AtomicInteger rateLimit = new AtomicInteger(Integer.MAX_VALUE);
    private final AtomicInteger burstLimit = new AtomicInteger(Integer.MAX_VALUE);

    // Social Constants
    private static final int MAX_PAGING_ITERATIONS = 10;
    private static final int MAX_LIMIT = 40;
    private static final int DEFAULT_LIMIT = 20;
    private static final int ANCESTORS_LIMIT = 20;
    private static final int DESCENDANTS_LIMIT = 20;
    private static final int STREAM_QUERY_LIMIT = 50;

    // Modules
    public static final String CORE_MODULE_NAME = Core.class.getName();
    public static final String RELATIONSHIPS_MODULE_NAME = Relationships.class.getName();
    public static final String HASHTAGS_MODULE_NAME = TrendsAndHashtags.class.getName();
    public static final String SEARCH_MODULE_NAME = Search.class.getName();
    public static final String NOTIFICATIONS_MODULE_NAME = Notifications.class.getName();
    public static final String GLOBAL_TIMELINES_MODULE_NAME = GlobalTimelines.class.getName();
    public static final String ESPORTS_MODULE_NAME = ESports.class.getName();

    // Core Depots
    private final Depot accountDepot;
    private final Depot accountEditDepot;
    private final Depot statusDepot;
    private final Depot scheduledStatusDepot;
    private final Depot conversationDepot;
    private final Depot likeStatusDepot;
    private final Depot bookmarkStatusDepot;
    private final Depot muteStatusDepot;
    private final Depot pinStatusDepot;
    private final Depot pollVoteDepot;
    private final Depot statusAttachmentWithIdDepot;
    private final Depot applicationDepot;

    // Core PStates
    private final PState nameToUser;
    private final PState pinnerToStatusIds;
    private final PState uuidToAttachment;
    private final PState postUUIDToStatusId;
    private final PState accountIdToScheduledStatuses;
    private final PState accountIdToConvoIds;
    private final PState accountIdToStatuses;
    private final PState bookmarkerToStatusPointers;
    private final PState likerToStatusPointers;
    private final PState statusIdToBoosters;
    private final PState statusIdToLikers;
    private final PState accountIdToAttachmentStatusIds;
    private final PState accountIdToDirectMessages;
    private final PState statusIdToConvoId;
    private final PState accountIdToDirectMessagesById;

    // Core Queries
    private final QueryTopologyClient<List<AccountWithId>> getAccountsFromAccountIds;
    private final QueryTopologyClient<StatusQueryResults> getAccountTimeline;
    private final QueryTopologyClient<StatusQueryResults> getStatusesFromPointers;
    private final QueryTopologyClient<Conversation> getConversation;
    private final QueryTopologyClient<List<Conversation>> getConversationTimeline;
    private final QueryTopologyClient<StatusQueryResults> getAncestors;
    private final QueryTopologyClient<StatusQueryResults> getDescendants;
    private final QueryTopologyClient<List<AccountWithId>> getAccountsFromNames;
    private final QueryTopologyClient<StatusQueryResults> getHomeTimeline;
    private final QueryTopologyClient<StatusQueryResults> getDirectTimeline;
    private final QueryTopologyClient<Map<Integer, List<StatusPointer>>> getHomeTimelinesUntil;
    private final QueryTopologyClient<Application> getApplicationFromClientId;

    // Relationships Depots
    private final Depot authCodeDepot;
    private final Depot followAndBlockAccountDepot;
    private final Depot muteAccountDepot;
    private final Depot featureAccountDepot;
    private final Depot filterDepot;
    private final Depot removeFollowSuggestionDepot;
    private final Depot followHashtagDepot;
    private final Depot followSpaceDepot;

    // Relationship PStates
    private final PState authCodeToAccountId;
    private final PState followerToFolloweesById;
    private final PState followeeToFollowersById;
    private final PState accountIdToFollowRequests;
    private final PState accountIdToFollowRequestsById;
    private final PState accountIdToSuppressions;
    private final PState postUUIDToGeneratedId;
    private final PState accountIdToFilterIdToFilter;
    private final PState hashtagToFollowers;
    private final PState spaceToFollowers;

    // Relationship Queries
    private final QueryTopologyClient<AccountRelationshipQueryResult> getAccountRelationship;
    private final QueryTopologyClient<List<Long>> getFamiliarFollowers;
    private final QueryTopologyClient<Set<Long>> getWhoToFollowSuggestions;

    // Notifications Depots
    private final Depot dismissDepot;

    // Notifications PStates
    private final PState accountIdToNotificationsTimeline;

    // Hashtag PStates
    private final PState hashtagTrends;
    private final PState spaceTrends;
    private final PState statusTrends;
    private final PState accountIdToHashtagActivity;
    private final PState hashtagToStatusPointers;
    private final PState hashtagToStatusPointersReverse;
    private final PState spaceToStatusPointersReverse;

    // Hashtag Queries
    private final QueryTopologyClient<Map<String, ItemStats>> batchHashtagStats;
    private final QueryTopologyClient<StatusQueryResults> getHashtagTimeline;
    private final QueryTopologyClient<Map<String, ItemStats>> batchSpaceStats;
    private final QueryTopologyClient<StatusQueryResults> getSpaceTimeline;

    // Search PStates
    private final PState activeAccountIds;
    private final PState newAccountIds;

    // Search Queries
    private final QueryTopologyClient<Map> profileTermsSearch;
    private final QueryTopologyClient<Map> statusTermsSearch;
    private final QueryTopologyClient<Map> hashtagSearch;

    // Global Timelines PStates
    private final PState localTimeline;

    // ESports Depots
    private final Depot seriesDepot;
    private final Depot matchDepot;
    private final Depot rosterDepot;
    private final Depot teamDepot;
    private final Depot scheduleDepot;
    private final Depot playerDepot;
    private final Depot lolMatchSummaryDepot;
    private final Depot lolPlayerSeasonStatsDepot;
    private final Depot lolTeamSeasonStatsDepot;
    private final Depot assetDepot;
    private final Depot liveLolMatchSummaryDepot;
    private final Depot tournamentDepot;
    private final Depot substageDepot;
    private final Depot casterDepot;
    private final Depot lolTeamAggStatsDepot;
    private final Depot lolPlayerAggStatsDepot;

    // ESports Queries
    private final QueryTopologyClient<Series> getSeriesFromSeriesId;
    private final QueryTopologyClient<Map> getSeriesFromStartTime;
    private final QueryTopologyClient<Match> getMatchFromMatchId;
    private final QueryTopologyClient<Roster> getRosterFromRosterId;
    private final QueryTopologyClient<Team> getTeamFromTeamId;
    private final QueryTopologyClient<List<Integer>> getSeriesIdsFromTeamId;
    private final QueryTopologyClient<List<Integer>> getAllTeamIds;
    private final QueryTopologyClient<Player> getPlayerFromPlayerId;
    private final QueryTopologyClient<List<Integer>> getAllPlayerIds;
    private final QueryTopologyClient<LolMatchSummary> getLolMatchSummaryFromMatchId;
    private final QueryTopologyClient<Asset> getAssetFromAssetId;
    private final QueryTopologyClient<List<LolPlayerSummary>> getLolPlayerSeasonStatsFromPlayerId;
    private final QueryTopologyClient<List<LolTeamSummary>> getLolTeamSeasonStatsFromTeamId;
    private final QueryTopologyClient<Tournament> getTournamentFromTournamentId;
    private final QueryTopologyClient<Substage> getSubstageFromSubstageId;
    private final QueryTopologyClient<Caster> getCasterFromCasterId;
    private final QueryTopologyClient<LiveLolMatchSummary> getLiveLolMatchSummaryFromMatchId;
    private final QueryTopologyClient<LolTeamAggStats> getLolTeamAggStatsFromTeamId;
    private final QueryTopologyClient<LolPlayerAggStats> getLolPlayerAggStatsFromPlayerId;

    public ApolloApiManager(ClusterManagerBase cluster) {

        this.apiClient = new AbiosApiClient(ABIOS_SECRET);

        this.objectMapper = new ObjectMapper();
        this.objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        this.objectMapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);

        this.objectMapper.registerModule(new JavaTimeModule());

        // Core Depots
        accountDepot = cluster.clusterDepot(CORE_MODULE_NAME, "*accountDepot");
        accountEditDepot = cluster.clusterDepot(CORE_MODULE_NAME, "*accountEditDepot");
        statusDepot = cluster.clusterDepot(CORE_MODULE_NAME, "*statusDepot");
        scheduledStatusDepot = cluster.clusterDepot(CORE_MODULE_NAME, "*scheduledStatusDepot");
        conversationDepot = cluster.clusterDepot(CORE_MODULE_NAME, "*conversationDepot");
        likeStatusDepot = cluster.clusterDepot(CORE_MODULE_NAME, "*likeStatusDepot");
        bookmarkStatusDepot = cluster.clusterDepot(CORE_MODULE_NAME, "*bookmarkStatusDepot");
        muteStatusDepot = cluster.clusterDepot(CORE_MODULE_NAME, "*muteStatusDepot");
        pinStatusDepot = cluster.clusterDepot(CORE_MODULE_NAME, "*pinStatusDepot");
        pollVoteDepot = cluster.clusterDepot(CORE_MODULE_NAME, "*pollVoteDepot");
        statusAttachmentWithIdDepot = cluster.clusterDepot(CORE_MODULE_NAME, "*statusAttachmentWithIdDepot");
        applicationDepot = cluster.clusterDepot(CORE_MODULE_NAME, "*applicationDepot");

        // Core PStates
        nameToUser = cluster.clusterPState(CORE_MODULE_NAME, "$$nameToUser");
        pinnerToStatusIds = cluster.clusterPState(CORE_MODULE_NAME, "$$pinnerToStatusIds");
        accountIdToScheduledStatuses = cluster.clusterPState(CORE_MODULE_NAME, "$$accountIdToScheduledStatuses");
        postUUIDToStatusId = cluster.clusterPState(CORE_MODULE_NAME, "$$postUUIDToStatusId");
        uuidToAttachment = cluster.clusterPState(CORE_MODULE_NAME, "$$uuidToAttachment");
        accountIdToConvoIds = cluster.clusterPState(CORE_MODULE_NAME, "$$accountIdToConvoIds");
        accountIdToStatuses = cluster.clusterPState(CORE_MODULE_NAME, "$$accountIdToStatuses");
        bookmarkerToStatusPointers = cluster.clusterPState(CORE_MODULE_NAME, "$$bookmarkerToStatusPointers");
        likerToStatusPointers = cluster.clusterPState(CORE_MODULE_NAME, "$$likerToStatusPointers");
        statusIdToBoosters = cluster.clusterPState(CORE_MODULE_NAME, "$$statusIdToBoosters");
        statusIdToLikers = cluster.clusterPState(CORE_MODULE_NAME, "$$statusIdToLikers");
        accountIdToAttachmentStatusIds = cluster.clusterPState(CORE_MODULE_NAME, "$$accountIdToAttachmentStatusIds");
        accountIdToDirectMessages = cluster.clusterPState(CORE_MODULE_NAME, "$$accountIdToDirectMessages");
        statusIdToConvoId = cluster.clusterPState(CORE_MODULE_NAME, "$$statusIdToConvoId");
        accountIdToDirectMessagesById = cluster.clusterPState(CORE_MODULE_NAME, "$$accountIdToDirectMessagesById");

        // Core Queries
        getAccountsFromAccountIds = cluster.clusterQuery(CORE_MODULE_NAME, "getAccountsFromAccountIds");
        getAccountTimeline = cluster.clusterQuery(CORE_MODULE_NAME, "getAccountTimeline");
        getStatusesFromPointers = cluster.clusterQuery(CORE_MODULE_NAME, "getStatusesFromPointers");
        getConversation = cluster.clusterQuery(CORE_MODULE_NAME, "getConversation");
        getConversationTimeline = cluster.clusterQuery(CORE_MODULE_NAME, "getConversationTimeline");
        getAncestors = cluster.clusterQuery(CORE_MODULE_NAME, "getAncestors");
        getDescendants = cluster.clusterQuery(CORE_MODULE_NAME, "getDescendants");
        getAccountsFromNames = cluster.clusterQuery(CORE_MODULE_NAME, "getAccountsFromNames");
        getHomeTimeline = cluster.clusterQuery(CORE_MODULE_NAME, "getHomeTimeline");
        getDirectTimeline = cluster.clusterQuery(CORE_MODULE_NAME, "getDirectTimeline");
        getHomeTimelinesUntil = cluster.clusterQuery(CORE_MODULE_NAME, "getHomeTimelinesUntil");
        getApplicationFromClientId = cluster.clusterQuery(CORE_MODULE_NAME, "getApplicationFromClientId");

        // Relationships Depots
        authCodeDepot = cluster.clusterDepot(RELATIONSHIPS_MODULE_NAME, "*authCodeDepot");
        followAndBlockAccountDepot = cluster.clusterDepot(RELATIONSHIPS_MODULE_NAME, "*followAndBlockAccountDepot");
        muteAccountDepot = cluster.clusterDepot(RELATIONSHIPS_MODULE_NAME, "*muteAccountDepot");
        featureAccountDepot = cluster.clusterDepot(RELATIONSHIPS_MODULE_NAME, "*featureAccountDepot");
        filterDepot = cluster.clusterDepot(RELATIONSHIPS_MODULE_NAME, "*filterDepot");
        removeFollowSuggestionDepot = cluster.clusterDepot(RELATIONSHIPS_MODULE_NAME, "*removeFollowSuggestionDepot");
        followHashtagDepot = cluster.clusterDepot(RELATIONSHIPS_MODULE_NAME, "*followHashtagDepot");
        followSpaceDepot = cluster.clusterDepot(RELATIONSHIPS_MODULE_NAME, "*followSpaceDepot");

        // Relationship PStates
        authCodeToAccountId = cluster.clusterPState(RELATIONSHIPS_MODULE_NAME, "$$authCodeToAccountId");
        followerToFolloweesById = cluster.clusterPState(RELATIONSHIPS_MODULE_NAME, "$$followerToFolloweesById");
        followeeToFollowersById = cluster.clusterPState(RELATIONSHIPS_MODULE_NAME, "$$followeeToFollowersById");
        accountIdToFollowRequests = cluster.clusterPState(RELATIONSHIPS_MODULE_NAME, "$$accountIdToFollowRequests");
        accountIdToFollowRequestsById = cluster.clusterPState(RELATIONSHIPS_MODULE_NAME,
                "$$accountIdToFollowRequestsById");
        accountIdToSuppressions = cluster.clusterPState(RELATIONSHIPS_MODULE_NAME, "$$accountIdToSuppressions");
        postUUIDToGeneratedId = cluster.clusterPState(RELATIONSHIPS_MODULE_NAME, "$$postUUIDToGeneratedId");
        accountIdToFilterIdToFilter = cluster.clusterPState(RELATIONSHIPS_MODULE_NAME, "$$accountIdToFilterIdToFilter");
        hashtagToFollowers = cluster.clusterPState(RELATIONSHIPS_MODULE_NAME, "$$hashtagToFollowers");
        spaceToFollowers = cluster.clusterPState(RELATIONSHIPS_MODULE_NAME, "$$spaceToFollowers");

        // Relationships Queries
        getAccountRelationship = cluster.clusterQuery(RELATIONSHIPS_MODULE_NAME, "getAccountRelationship");
        getFamiliarFollowers = cluster.clusterQuery(RELATIONSHIPS_MODULE_NAME, "getFamiliarFollowers");
        getWhoToFollowSuggestions = cluster.clusterQuery(RELATIONSHIPS_MODULE_NAME, "getWhoToFollowSuggestions");

        // Notifications Depots
        dismissDepot = cluster.clusterDepot(NOTIFICATIONS_MODULE_NAME, "*dismissDepot");

        // Notifications PStates
        accountIdToNotificationsTimeline = cluster.clusterPState(NOTIFICATIONS_MODULE_NAME,
                "$$accountIdToNotificationsTimeline");

        // Hashtag/Trends PStates
        hashtagTrends = cluster.clusterPState(HASHTAGS_MODULE_NAME, "$$hashtagTrends");
        statusTrends = cluster.clusterPState(HASHTAGS_MODULE_NAME, "$$statusTrends");
        accountIdToHashtagActivity = cluster.clusterPState(HASHTAGS_MODULE_NAME, "$$accountIdToHashtagActivity");
        hashtagToStatusPointers = cluster.clusterPState(HASHTAGS_MODULE_NAME, "$$hashtagToStatusPointers");
        hashtagToStatusPointersReverse = cluster.clusterPState(HASHTAGS_MODULE_NAME,
                "$$hashtagToStatusPointersReverse");
        spaceTrends = cluster.clusterPState(HASHTAGS_MODULE_NAME, "$$spaceTrends");
        spaceToStatusPointersReverse = cluster.clusterPState(HASHTAGS_MODULE_NAME,
                "$$spaceToStatusPointersReverse");

        // Hashtag Queries
        batchHashtagStats = cluster.clusterQuery(HASHTAGS_MODULE_NAME, "batchHashtagStats");
        getHashtagTimeline = cluster.clusterQuery(HASHTAGS_MODULE_NAME, "getHashtagTimeline");
        batchSpaceStats = cluster.clusterQuery(HASHTAGS_MODULE_NAME, "batchSpaceStats");
        getSpaceTimeline = cluster.clusterQuery(HASHTAGS_MODULE_NAME, "getSpaceTimeline");

        // Search PStates
        activeAccountIds = cluster.clusterPState(SEARCH_MODULE_NAME, "$$activeAccountIds");
        newAccountIds = cluster.clusterPState(SEARCH_MODULE_NAME, "$$newAccountIds");

        // Search Queries
        profileTermsSearch = cluster.clusterQuery(SEARCH_MODULE_NAME, "profileTermsSearch");
        statusTermsSearch = cluster.clusterQuery(SEARCH_MODULE_NAME, "statusTermsSearch");
        hashtagSearch = cluster.clusterQuery(SEARCH_MODULE_NAME, "hashtagSearch");

        // Global Timelines PStates
        localTimeline = cluster.clusterPState(GLOBAL_TIMELINES_MODULE_NAME, "$$localTimeline");

        // ESports Depots
        seriesDepot = cluster.clusterDepot(ESPORTS_MODULE_NAME, "*seriesDepot");
        matchDepot = cluster.clusterDepot(ESPORTS_MODULE_NAME, "*matchDepot");
        rosterDepot = cluster.clusterDepot(ESPORTS_MODULE_NAME, "*rosterDepot");
        teamDepot = cluster.clusterDepot(ESPORTS_MODULE_NAME, "*teamDepot");
        scheduleDepot = cluster.clusterDepot(ESPORTS_MODULE_NAME, "*scheduleDepot");
        playerDepot = cluster.clusterDepot(ESPORTS_MODULE_NAME, "*playerDepot");
        lolMatchSummaryDepot = cluster.clusterDepot(ESPORTS_MODULE_NAME, "*lolMatchSummaryDepot");
        lolPlayerSeasonStatsDepot = cluster.clusterDepot(ESPORTS_MODULE_NAME, "*lolPlayerSeasonStatsDepot");
        lolTeamSeasonStatsDepot = cluster.clusterDepot(ESPORTS_MODULE_NAME, "*lolTeamSeasonStatsDepot");
        assetDepot = cluster.clusterDepot(ESPORTS_MODULE_NAME, "*assetDepot");
        liveLolMatchSummaryDepot = cluster.clusterDepot(ESPORTS_MODULE_NAME, "*liveLolMatchSummaryDepot");
        tournamentDepot = cluster.clusterDepot(ESPORTS_MODULE_NAME, "*tournamentDepot");
        substageDepot = cluster.clusterDepot(ESPORTS_MODULE_NAME, "*substageDepot");
        casterDepot = cluster.clusterDepot(ESPORTS_MODULE_NAME, "*casterDepot");
        lolTeamAggStatsDepot = cluster.clusterDepot(ESPORTS_MODULE_NAME, "*lolTeamAggStatsDepot");
        lolPlayerAggStatsDepot = cluster.clusterDepot(ESPORTS_MODULE_NAME, "*lolPlayerAggStatsDepot");

        // ESports Queries
        getSeriesFromSeriesId = cluster.clusterQuery(ESPORTS_MODULE_NAME, "getSeriesFromSeriesId");
        getSeriesFromStartTime = cluster.clusterQuery(ESPORTS_MODULE_NAME, "getSeriesFromStartTime");
        getMatchFromMatchId = cluster.clusterQuery(ESPORTS_MODULE_NAME, "getMatchFromMatchId");
        getRosterFromRosterId = cluster.clusterQuery(ESPORTS_MODULE_NAME, "getRosterFromRosterId");
        getTeamFromTeamId = cluster.clusterQuery(ESPORTS_MODULE_NAME, "getTeamFromTeamId");
        getSeriesIdsFromTeamId = cluster.clusterQuery(ESPORTS_MODULE_NAME, "getSeriesIdsFromTeamId");
        getAllTeamIds = cluster.clusterQuery(ESPORTS_MODULE_NAME, "getAllTeamIds");
        getPlayerFromPlayerId = cluster.clusterQuery(ESPORTS_MODULE_NAME, "getPlayerFromPlayerId");
        getAllPlayerIds = cluster.clusterQuery(ESPORTS_MODULE_NAME, "getAllPlayerIds");
        getLolMatchSummaryFromMatchId = cluster.clusterQuery(ESPORTS_MODULE_NAME, "getLolMatchSummaryFromMatchId");
        getAssetFromAssetId = cluster.clusterQuery(ESPORTS_MODULE_NAME, "getAssetFromAssetId");
        getLolPlayerSeasonStatsFromPlayerId = cluster.clusterQuery(ESPORTS_MODULE_NAME,
                "getLolPlayerSeasonStatsFromPlayerId");
        getLolTeamSeasonStatsFromTeamId = cluster.clusterQuery(ESPORTS_MODULE_NAME, "getLolTeamSeasonStatsFromTeamId");
        getTournamentFromTournamentId = cluster.clusterQuery(ESPORTS_MODULE_NAME, "getTournamentFromTournamentId");
        getSubstageFromSubstageId = cluster.clusterQuery(ESPORTS_MODULE_NAME, "getSubstageFromSubstageId");
        getCasterFromCasterId = cluster.clusterQuery(ESPORTS_MODULE_NAME, "getCasterFromCasterId");
        getLiveLolMatchSummaryFromMatchId = cluster.clusterQuery(ESPORTS_MODULE_NAME,
                "getLiveLolMatchSummaryFromMatchId");
        getLolTeamAggStatsFromTeamId = cluster.clusterQuery(ESPORTS_MODULE_NAME, "getLolTeamAggStatsFromTeamId");
        getLolPlayerAggStatsFromPlayerId = cluster.clusterQuery(ESPORTS_MODULE_NAME,
                "getLolPlayerAggStatsFromPlayerId");

    }

    public static class QueryResults<T, O> {
        public List<T> results;
        public boolean reachedEnd;
        public O offset; // offset to use in the next query
        public List<SimpleEntry<String, String>> linkHeaderParams; // query params to send to the client via the Link
                                                                   // header

        public QueryResults(List<T> results, boolean reachedEnd, O offset,
                List<SimpleEntry<String, String>> linkHeaderParams) {
            this.results = results;
            this.reachedEnd = reachedEnd;
            this.offset = offset;
            this.linkHeaderParams = linkHeaderParams;
        }
    }

    CompletableFuture<Status> createStatusFromParams(long accountId, PostStatus params) {
        List<CompletableFuture<Object>> mediaFutures = params.media_ids.stream()
                .distinct()
                .map(attachmentId -> uuidToAttachment.selectOneAsync(Path.key(attachmentId)))
                .collect(Collectors.toList());
        List<CompletableFuture> mentionFutures = new ArrayList<>();

        return CompletableFuture.allOf(mediaFutures.toArray(new CompletableFuture<?>[0]))
                .allOf(mentionFutures.toArray(new CompletableFuture<?>[0]))
                .thenApply(_result -> {
                    List<AttachmentWithId> attachments = new ArrayList<>();
                    for (int i = 0; i < mediaFutures.size(); i++) {
                        attachments.add(
                                new AttachmentWithId(params.media_ids.get(i), (Attachment) mediaFutures.get(i).join()));
                    }

                    // create status
                    StatusVisibility visibility = ApolloApiHelpers.createStatusVisibility(params.visibility);
                    long ts = System.currentTimeMillis();
                    final Status status;
                    if (params.in_reply_to_id != null) {
                        StatusPointer parentPointer = ApolloHelpers.parseStatusPointer(params.in_reply_to_id);
                        ReplyStatusContent content = new ReplyStatusContent(params.status, visibility, parentPointer);
                        content.setAttachments(attachments);
                        if (params.poll != null)
                            content.setPollContent(new PollContent(params.poll.options,
                                    ts + (params.poll.expires_in * 1000), params.poll.multiple));
                        if (params.sensitive != null && params.sensitive)
                            content.setSensitiveWarning(params.spoiler_text != null ? params.spoiler_text : "");
                        status = new Status(accountId, StatusContent.reply(content), ts);
                    } else {
                        NormalStatusContent content = new NormalStatusContent(params.status, visibility);
                        content.setAttachments(attachments);
                        if (params.poll != null)
                            content.setPollContent(new PollContent(params.poll.options,
                                    ts + (params.poll.expires_in * 1000), params.poll.multiple));
                        if (params.sensitive != null && params.sensitive)
                            content.setSensitiveWarning(params.spoiler_text != null ? params.spoiler_text : "");
                        status = new Status(accountId, StatusContent.normal(content), ts);
                    }

                    return status;
                });
    }

    public static CompletableFuture<StatusQueryResults> queryStatusesWithPaging(
            BiFunction<StatusPointer, Integer, CompletableFuture<StatusQueryResults>> fn, StatusPointer offsetMaybe,
            Integer limitMaybe, int iterationsLeft) {
        if (iterationsLeft == 0)
            return CompletableFuture
                    .completedFuture(new StatusQueryResults(new ArrayList(), new HashMap(), true, false));

        StatusPointer offset = offsetMaybe == null ? new StatusPointer(-1, -1) : offsetMaybe;
        int limit = Math.min(limitMaybe == null ? DEFAULT_LIMIT : limitMaybe, MAX_LIMIT);

        return fn.apply(offset, limit)
                .thenCompose(statusQueryResults -> {
                    // if the results are less than the limit and we haven't reached the end...
                    if (statusQueryResults.results.size() < limit && !statusQueryResults.reachedEnd) {
                        StatusPointer nextOffset;
                        int nextLimit = limit - statusQueryResults.results.size();
                        if (statusQueryResults.isSetLastStatusPointer())
                            nextOffset = statusQueryResults.lastStatusPointer;
                        else
                            return CompletableFuture.completedFuture(statusQueryResults);
                        // recursively make the new request and concat the results.
                        return queryStatusesWithPaging(fn, nextOffset, nextLimit, iterationsLeft - 1)
                                .thenApply(nextResults -> {
                                    List<StatusResultWithId> results = new ArrayList<>(statusQueryResults.results);
                                    results.addAll(nextResults.results);
                                    HashMap<String, AccountWithId> mentions = new HashMap<>(
                                            statusQueryResults.mentions);
                                    mentions.putAll(nextResults.mentions);
                                    StatusQueryResults combinedResults = new StatusQueryResults(results, mentions,
                                            nextResults.reachedEnd, nextResults.refreshed);
                                    if (nextResults.isSetLastStatusPointer())
                                        combinedResults.setLastStatusPointer(nextResults.lastStatusPointer);
                                    return combinedResults;
                                });
                    } else
                        return CompletableFuture.completedFuture(statusQueryResults);
                });
    }

    public CompletableFuture<Boolean> postAuthCode(long accountId, String code) {
        return authCodeDepot.appendAsync(new AddAuthCode(code, accountId)).thenApply(res -> true);
    }

    public CompletableFuture<Long> getAccountId(String username) {
        return nameToUser.selectOneAsync(Path.key(username, "accountId"));
    }

    public CompletableFuture<AccountWithId> getAccountWithId(Long requestAccountIdMaybe, long accountId) {
        return getAccountsFromAccountIds.invokeAsync(requestAccountIdMaybe, Arrays.asList(accountId))
                .thenApply(accountWithIds -> {
                    if (accountWithIds.size() == 0)
                        return null;
                    return accountWithIds.get(0);
                });
    }

    public CompletableFuture<AccountWithId> getAccountWithId(long accountId) {
        return this.getAccountWithId(null, accountId);
    }

    public CompletableFuture<Boolean> postRemoveAuthCode(String code) {
        return authCodeDepot.appendAsync(new RemoveAuthCode(code)).thenApply(res -> true);
    }

    public CompletableFuture<Boolean> postAccount(PostAccount params) {
        String pwdHash = ApolloApiHelpers.encodePassword(params.password);
        String uuid = UUID.randomUUID().toString();
        final ApolloWebHelpers.SigningKeyPair keys;
        try {
            keys = ApolloWebHelpers.generateKeys();
        } catch (NoSuchProviderException | NoSuchAlgorithmException | IOException e) {
            return CompletableFuture.completedFuture(false);
        }
        return accountDepot
                .appendAsync(new Account(params.username, params.email, pwdHash, params.locale, uuid, keys.publicKey,
                        System.currentTimeMillis()))
                .thenCompose(res -> this.getAccountUUID(params.username))
                .thenApply(accountUUID -> accountUUID.equals(uuid));
    }

    public CompletableFuture<String> getAccountUUID(String username) {
        return nameToUser.selectOneAsync(Path.key(username, "uuid"));
    }

    public CompletableFuture<StatusQueryResults> getAccountTimeline(Long requestAccountIdMaybe, long timelineAccountId,
            StatusPointer offsetMaybe, Integer limitMaybe, boolean includeReplies, boolean includeBoosts) {
        return this.getPinnedStatuses(requestAccountIdMaybe, timelineAccountId)
                .thenCompose(pinnedStatuses -> {
                    Set<Long> pinnedIds = pinnedStatuses.results.stream().map(o -> o.statusId)
                            .collect(Collectors.toSet());
                    return queryStatusesWithPaging((offset, limit) -> getAccountTimeline
                            .invokeAsync(requestAccountIdMaybe, timelineAccountId, offset.statusId, limit,
                                    includeReplies)
                            .thenApply(statusQueryResults -> {
                                if (pinnedIds.size() > 0)
                                    statusQueryResults.results = statusQueryResults.results.stream()
                                            .filter(statusResult -> !pinnedIds.contains(statusResult.statusId))
                                            .collect(Collectors.toList());
                                if (!includeBoosts)
                                    statusQueryResults.results = statusQueryResults.results.stream()
                                            .filter(statusResult -> !statusResult.status.content.isSetBoost())
                                            .collect(Collectors.toList());
                                return statusQueryResults;
                            }),
                            offsetMaybe, limitMaybe, MAX_PAGING_ITERATIONS);
                });
    }

    public CompletableFuture<StatusQueryResults> getPinnedStatuses(Long requestAccountIdMaybe, long authorId) {
        return pinnerToStatusIds.selectAsync(Path.key(authorId).mapVals())
                .thenCompose(statusIds -> {
                    List<StatusPointer> pointers = new ArrayList<>();
                    for (Object statusId : statusIds)
                        pointers.add(new StatusPointer(authorId, (Long) statusId));
                    QueryFilterOptions filterOptions = new QueryFilterOptions(FilterContext.Public, false);
                    return getStatusesFromPointers.invokeAsync(requestAccountIdMaybe, pointers, filterOptions);
                });
    }

    public CompletableFuture<StatusQueryResult> postStatus(long accountId, PostStatus params) {
        String uuid = UUID.randomUUID().toString();
        return createStatusFromParams(accountId, params)
                .thenComposeAsync(status -> {
                    AddStatus addStatus = new AddStatus(uuid, status);
                    return statusDepot.appendAsync(addStatus);
                })
                .thenCompose(res -> postUUIDToStatusId.selectOneAsync(accountId, Path.key(uuid)))
                .thenCompose(statusId -> {
                    if (statusId == null)
                        return CompletableFuture.completedFuture(null);
                    StatusPointer newPointer = new StatusPointer(accountId, (Long) statusId);
                    return this.getStatus(accountId, newPointer);
                });
    }

    public CompletableFuture<StatusWithId> postScheduledStatus(long accountId, PostStatus params, Object object) {
        String uuid = UUID.randomUUID().toString();
        return createStatusFromParams(accountId, params)
                .thenComposeAsync(status -> {
                    AddScheduledStatus addScheduledStatus = new AddScheduledStatus(uuid, status,
                            Instant.parse(params.scheduled_at).toEpochMilli());
                    return scheduledStatusDepot.appendAsync(addScheduledStatus);
                })
                .thenCompose(res -> postUUIDToStatusId.selectOneAsync(accountId, Path.key(uuid)))
                .thenCompose(statusId -> {
                    if (statusId == null)
                        return CompletableFuture.completedFuture(null);
                    return accountIdToScheduledStatuses.selectOneAsync(Path.key(accountId, statusId, "status"))
                            .thenApply(status -> new StatusWithId((long) statusId, (Status) status));
                });
    }

    public CompletableFuture<StatusQueryResult> getStatus(Long requestAccountIdMaybe, StatusPointer pointer,
            QueryFilterOptions filterOptions) {
        return getStatusesFromPointers.invokeAsync(requestAccountIdMaybe,
                PersistentVector.EMPTY.cons(new StatusPointer(pointer.authorId, pointer.statusId)), filterOptions)
                .thenApply(statusQueryResults -> {
                    if (statusQueryResults.results.size() == 0)
                        return null;
                    StatusResultWithId result = statusQueryResults.results.get(0);
                    return new StatusQueryResult(result, statusQueryResults.mentions);
                });
    }

    public CompletableFuture<StatusQueryResult> getStatus(Long requestAccountIdMaybe, StatusPointer pointer) {
        QueryFilterOptions filterOptions = new QueryFilterOptions(FilterContext.Public, false);
        return this.getStatus(requestAccountIdMaybe, pointer, filterOptions);
    }

    public CompletableFuture<StatusWithId> getScheduledStatus(StatusPointer statusPointer) {
        return accountIdToScheduledStatuses
                .selectOneAsync(Path.key(statusPointer.authorId, statusPointer.statusId, "status"))
                .thenApply(status -> {
                    if (status == null)
                        return null;
                    return new StatusWithId(statusPointer.statusId, (Status) status);
                });
    }

    public CompletableFuture<QueryResults<StatusWithId, Long>> getScheduledStatuses(Long accountId,
            StatusPointer offsetMaybe, Integer limitMaybe) {
        long offset = offsetMaybe == null ? -1L : offsetMaybe.statusId;
        int limit = Math.min(limitMaybe == null ? DEFAULT_LIMIT : limitMaybe, MAX_LIMIT);
        SortedRangeFromOptions options = SortedRangeFromOptions.excludeStart().maxAmt(limit);
        return accountIdToScheduledStatuses
                .selectAsync(Path.key(accountId)
                        .sortedMapRangeFrom(offset, options)
                        .all()
                        .collectOne(Path.first())
                        .last()
                        .key("status"))
                .thenApply((List<Object> results) -> {
                    List<StatusWithId> statuses = results.stream()
                            .map(result -> {
                                List<Object> resultList = (List<Object>) result;
                                Long statusId = (Long) resultList.get(0);
                                Status status = (Status) resultList.get(1);
                                return new StatusWithId(statusId, status);
                            }).collect(Collectors.toList());
                    Long lastId = null;
                    List<SimpleEntry<String, String>> linkHeaderParams = null;
                    if (statuses.size() > 0) {
                        StatusWithId lastStatus = statuses.get(statuses.size() - 1);
                        lastId = lastStatus.statusId;
                        linkHeaderParams = Arrays
                                .asList(new SimpleEntry<>("max_id", ApolloHelpers.serializeStatusPointer(
                                        new StatusPointer(lastStatus.status.authorId, lastStatus.statusId))));
                    }
                    return new QueryResults<>(statuses, results.size() < limit, lastId, linkHeaderParams);
                });
    }

    public CompletableFuture<StatusWithId> updateScheduledStatus(StatusPointer statusPointer, String scheduledAt) {
        long publishAt = Instant.parse(scheduledAt).toEpochMilli();
        long timestamp = Instant.now().toEpochMilli();
        return scheduledStatusDepot
                .appendAsync(new EditScheduledStatusPublishTime(statusPointer.authorId, statusPointer.statusId,
                        publishAt, timestamp))
                .thenComposeAsync(result -> {
                    return accountIdToScheduledStatuses.selectOneAsync(Path.key(statusPointer.authorId)
                            .must(statusPointer.statusId)
                            .collectOne(Path.key("publishMillis"))
                            .key("status"));
                }).thenApply(result -> {
                    List<Object> publishMillisAndStatus = (List<Object>) result;
                    Long publishMillis = (Long) publishMillisAndStatus.get(0);
                    Status status = (Status) publishMillisAndStatus.get(1);
                    status.timestamp = publishMillis;
                    return new StatusWithId(statusPointer.statusId, status);
                });
    }

    public CompletableFuture<Void> cancelScheduledStatus(StatusPointer statusPointer) {
        return scheduledStatusDepot.appendAsync(
                new RemoveStatus(statusPointer.authorId, statusPointer.statusId, Instant.now().toEpochMilli()))
                .thenApply(result -> null);
    }

    public CompletableFuture<SimpleEntry<AccountWithId, AccountWithId>> getAccountWithIdPair(long firstAccountId,
            long secondAccountId) {
        return getAccountsFromAccountIds.invokeAsync(null, Arrays.asList(firstAccountId, secondAccountId))
                .thenApply(accountWithIds -> {
                    if (accountWithIds.size() != 2)
                        return null;
                    return new SimpleEntry<>(accountWithIds.get(0), accountWithIds.get(1));
                });
    }

    public CompletableFuture<Boolean> postFollowAccount(long followerId, long followeeId, PostFollow params) {
        return getAccountWithId(followeeId)
                .thenCompose((followee) -> {
                    if (followee != null && followee.account != null && followee.account.locked) {
                        FollowLockedAccount req = new FollowLockedAccount(followeeId, followerId,
                                System.currentTimeMillis());
                        if (params != null) {
                            if (params.reposts != null)
                                req.setShowBoosts(params.reposts);
                            if (params.notify != null)
                                req.setNotify(params.notify);
                            if (params.languages != null)
                                req.setLanguages(params.languages);
                        }
                        return followAndBlockAccountDepot.appendAsync(req);
                    } else {
                        FollowAccount req = new FollowAccount(followerId, followeeId, System.currentTimeMillis());
                        if (params != null) {
                            if (params.reposts != null)
                                req.setShowBoosts(params.reposts);
                            if (params.notify != null)
                                req.setNotify(params.notify);
                            if (params.languages != null)
                                req.setLanguages(params.languages);
                        }
                        return followAndBlockAccountDepot.appendAsync(req);
                    }
                }).thenApply(res -> true);
    }

    public CompletableFuture<AccountRelationshipQueryResult> getAccountRelationship(long sourceId, long targetId) {
        return getAccountRelationship.invokeAsync(sourceId, targetId);
    }

    public CompletableFuture<Boolean> postRemoveFollowAccount(long followerId, long followeeId) {
        RemoveFollowAccount removeFollowAccount = new RemoveFollowAccount(followerId, followeeId,
                System.currentTimeMillis());
        return followAndBlockAccountDepot.appendAsync(removeFollowAccount).thenApply(res -> true);
    }

    public CompletableFuture<Boolean> postMuteAccount(long muterId, long muteeId, PostMute params) {
        MuteAccountOptions options = new MuteAccountOptions(params.notifications);
        if (params.duration != null)
            options.setExpirationMillis(System.currentTimeMillis() + params.duration * 1000);
        return muteAccountDepot.appendAsync(new MuteAccount(muterId, muteeId, options, System.currentTimeMillis()))
                .thenApply(res -> true);
    }

    public CompletableFuture<Boolean> postRemoveMuteAccount(long muterId, long muteeId) {
        return muteAccountDepot.appendAsync(new RemoveMuteAccount(muterId, muteeId, System.currentTimeMillis()))
                .thenApply(res -> true);
    }

    public CompletableFuture<Boolean> postBlockAccount(long blockerId, long blockeeId) {
        return followAndBlockAccountDepot
                .appendAsync(new BlockAccount(blockerId, blockeeId, System.currentTimeMillis()))
                .thenApply(res -> true);
    }

    public CompletableFuture<Boolean> postRemoveBlockAccount(long blockerId, long blockeeId) {
        return followAndBlockAccountDepot
                .appendAsync(new RemoveBlockAccount(blockerId, blockeeId, System.currentTimeMillis()))
                .thenApply(res -> true);
    }

    public CompletableFuture<Boolean> postFeatureAccount(long featurerId, long featureeId) {
        return featureAccountDepot.appendAsync(new FeatureAccount(featurerId, featureeId, System.currentTimeMillis()))
                .thenApply(res -> true);
    }

    public CompletableFuture<Boolean> postRemoveFeatureAccount(long featurerId, long featureeId) {
        return featureAccountDepot
                .appendAsync(new RemoveFeatureAccount(featurerId, featureeId, System.currentTimeMillis()))
                .thenApply(res -> true);
    }

    public CompletableFuture<Conversation> postConversation(long accountId, long conversationId, boolean unread) {
        return conversationDepot.appendAsync(new EditConversation(accountId, conversationId, unread))
                .thenCompose(result -> getConversation.invokeAsync(accountId, conversationId)
                        .thenApply(convoMaybe -> {
                            if (convoMaybe == null)
                                return null;
                            // the change was processed in a microbatch
                            // so the query won't necessarily return the
                            // most up-to-date value, so we're updating it manually.
                            convoMaybe.unread = unread;
                            return convoMaybe;
                        }));
    }

    public CompletableFuture<QueryResults<Conversation, Long>> getConversationTimeline(long accountId, Long offsetMaybe,
            Integer limitMaybe) {
        CompletableFuture<Long> timelineIndexFuture = offsetMaybe == null ? CompletableFuture.completedFuture(-1L)
                : accountIdToConvoIds.selectOneAsync(Path.key(accountId, offsetMaybe).nullToVal(-1L));
        int limit = Math.min(limitMaybe == null ? DEFAULT_LIMIT : limitMaybe, MAX_LIMIT);
        return timelineIndexFuture
                .thenCompose(timelineIndex -> getConversationTimeline.invokeAsync(accountId, timelineIndex, limit)
                        .thenApply(conversations -> {
                            Long lastId = null;
                            List<SimpleEntry<String, String>> linkHeaderParams = null;
                            if (conversations.size() > 0) {
                                lastId = conversations.get(conversations.size() - 1).conversationId;
                                linkHeaderParams = Arrays.asList(new SimpleEntry<>("max_id", lastId + ""));
                            }
                            return new QueryResults<>(conversations, conversations.size() < limit, lastId,
                                    linkHeaderParams);
                        }));
    }

    public CompletableFuture<Boolean> deleteConversation(long accountId, long conversationId) {
        return conversationDepot.appendAsync(new RemoveConversation(accountId, conversationId)).thenApply(res -> true);
    }

    public CompletableFuture<Long> getAccountIdFromAuthCode(String code) {
        return authCodeToAccountId.selectOneAsync(Path.key(code));
    }

    public CompletableFuture<Map<String, ItemStats>> getTrendingHashtags(Integer limitMaybe, Integer offsetMaybe) {
        long offset = offsetMaybe == null ? 0 : offsetMaybe;
        int defaultLimit = 10;
        int maxLimit = 20;
        int limit = Math.min(limitMaybe == null ? defaultLimit : limitMaybe, maxLimit);
        return hashtagTrends.selectAsync(Path.all().first())
                .thenApply(tags -> tags.stream().skip(offset).limit(limit).collect(Collectors.toList()))
                .thenCompose(batchHashtagStats::invokeAsync)
                .thenApply(res -> res == null ? new HashMap<>() : res);
    }

    public CompletableFuture<StatusQueryResults> getTrendingStatuses(Long requestAccountIdMaybe, Integer limitMaybe,
            Integer offsetMaybe) {
        long offset = offsetMaybe == null ? 0 : offsetMaybe;
        int limit = Math.min(limitMaybe == null ? DEFAULT_LIMIT : limitMaybe, MAX_LIMIT);
        return statusTrends.selectAsync(Path.all().first())
                .thenApply(statuses -> statuses.stream().skip(offset).limit(limit).collect(Collectors.toList()))
                .thenCompose(statusPointers -> getStatusesFromPointers.invokeAsync(requestAccountIdMaybe,
                        statusPointers, new QueryFilterOptions(FilterContext.Public, false)));
    }

    public CompletableFuture<QueryResults<AccountWithId, Long>> getAccountFollowees(long followerId, Long offsetMaybe,
            Integer limitMaybe) {
        long offset = offsetMaybe == null ? -1L : offsetMaybe;
        int limit = Math.min(limitMaybe == null ? DEFAULT_LIMIT : limitMaybe, MAX_LIMIT);
        SortedRangeFromOptions options = SortedRangeFromOptions.excludeStart().maxAmt(limit);
        CompletableFuture<List<List>> followeesFuture = followerToFolloweesById
                .selectAsync(Path.key(followerId).sortedMapRangeFrom(offset, options).all());
        return followeesFuture.thenCompose(keyVals -> getAccountWithTimelineIndexes(keyVals, limit))
                .thenApply(accountWithTimelineIndexes -> {
                    List<SimpleEntry<Long, AccountWithId>> results = accountWithTimelineIndexes.getKey();
                    List<AccountWithId> accountWithIds = results.stream().map(SimpleEntry::getValue)
                            .collect(Collectors.toList());
                    boolean reachedEnd = accountWithTimelineIndexes.getValue();
                    Long lastId = null;
                    List<SimpleEntry<String, String>> linkHeaderParams = null;
                    if (results.size() > 0) {
                        lastId = results.get(results.size() - 1).getKey();
                        linkHeaderParams = Arrays.asList(new SimpleEntry<>("max_id", lastId + ""));
                    }
                    return new QueryResults<>(accountWithIds, reachedEnd, lastId, linkHeaderParams);
                });
    }

    public CompletableFuture<QueryResults<AccountWithId, Long>> getAccountFollowers(long followeeId, Long offsetMaybe,
            Integer limitMaybe) {
        long offset = offsetMaybe == null ? -1L : offsetMaybe;
        int limit = Math.min(limitMaybe == null ? DEFAULT_LIMIT : limitMaybe, MAX_LIMIT);
        SortedRangeFromOptions options = SortedRangeFromOptions.excludeStart().maxAmt(limit);
        CompletableFuture<List<List>> followeesFuture = followeeToFollowersById
                .selectAsync(Path.key(followeeId).sortedMapRangeFrom(offset, options).all());
        return followeesFuture.thenCompose(keyVals -> getAccountWithTimelineIndexes(keyVals, limit))
                .thenApply(accountWithTimelineIndexes -> {
                    List<SimpleEntry<Long, AccountWithId>> results = accountWithTimelineIndexes.getKey();
                    List<AccountWithId> accountWithIds = results.stream().map(SimpleEntry::getValue)
                            .collect(Collectors.toList());
                    boolean reachedEnd = accountWithTimelineIndexes.getValue();
                    Long lastId = null;
                    List<SimpleEntry<String, String>> linkHeaderParams = null;
                    if (results.size() > 0) {
                        lastId = results.get(results.size() - 1).getKey();
                        linkHeaderParams = Arrays.asList(new SimpleEntry<>("max_id", lastId + ""));
                    }
                    return new QueryResults<>(accountWithIds, reachedEnd, lastId, linkHeaderParams);
                });
    }

    public CompletableFuture<SimpleEntry<List<SimpleEntry<Long, AccountWithId>>, Boolean>> getAccountWithTimelineIndexes(
            List<List> keyVals, long limit) {
        List<Long> accountIds = keyVals.stream().map(l -> ((Follower) l.get(1)).accountId).collect(Collectors.toList());
        return this.getAccountsFromAccountIds(accountIds)
                .thenApply(accountWithIds -> {
                    List<SimpleEntry<Long, AccountWithId>> accountWithTimelineIndexes = new ArrayList<>();
                    int i = 0;
                    for (AccountWithId accountWithId : accountWithIds) {
                        accountWithTimelineIndexes.add(new SimpleEntry<>((Long) keyVals.get(i).get(0), accountWithId));
                        i++;
                    }
                    return new SimpleEntry<>(accountWithTimelineIndexes, accountWithIds.size() < limit);
                });
    }

    private CompletableFuture<List<AccountWithId>> getAccountsFromAccountIds(List<Long> accountIds) {
        return getAccountsFromAccountIds.invokeAsync(null, accountIds);
    }

    public CompletableFuture<QueryResults<AccountWithId, Long>> getFollowRequests(long requestAccountId,
            Long offsetMaybe, Integer limitMaybe) {
        return queryWithPaging(
                (offset, limit) -> {
                    SortedRangeFromOptions options = SortedRangeFromOptions.excludeStart().maxAmt(limit);
                    CompletableFuture<List<Long>> future = accountIdToFollowRequestsById
                            .selectAsync(Path.key(requestAccountId).sortedMapRangeFrom(offset, options).mapVals()
                                    .customNav(new com.apollo.backend.navs.TField("requesterId")));
                    return future.thenCompose(
                            requesterIds -> getAccountsFromAccountIds.invokeAsync(requestAccountId, requesterIds))
                            .thenApply(accountWithIds -> {
                                Long lastId = null;
                                List<SimpleEntry<String, String>> linkHeaderParams = null;
                                if (accountWithIds.size() > 0) {
                                    AccountWithId lastAccount = accountWithIds.get(accountWithIds.size() - 1);
                                    lastId = lastAccount.accountId;
                                    linkHeaderParams = Arrays.asList(new SimpleEntry<>("max_id",
                                            ApolloHelpers.serializeAccountId(lastAccount.accountId)));
                                }
                                return new QueryResults<>(accountWithIds, accountWithIds.size() < limit, lastId,
                                        linkHeaderParams);
                            });
                },
                offsetMaybe == null ? -1L : offsetMaybe,
                Math.min(limitMaybe == null ? DEFAULT_LIMIT : limitMaybe, MAX_LIMIT),
                MAX_PAGING_ITERATIONS);
    }

    public CompletableFuture<Boolean> acceptFollowRequest(long accountId, long requesterId) {
        return accountIdToFollowRequests.selectOneAsync(Path.key(accountId, requesterId))
                .thenCompose(existingRequest -> {
                    if (existingRequest != null) {
                        return followAndBlockAccountDepot
                                .appendAsync(
                                        new AcceptFollowRequest(accountId, requesterId, System.currentTimeMillis()))
                                .thenApply(res -> true);
                    } else
                        return CompletableFuture.completedFuture(false);
                });
    }

    public CompletableFuture<Boolean> rejectFollowRequest(long accountId, long requesterId) {
        return accountIdToFollowRequests.selectOneAsync(Path.key(accountId, requesterId))
                .thenCompose(existingRequest -> {
                    if (existingRequest != null) {
                        return followAndBlockAccountDepot.appendAsync(new RejectFollowRequest(accountId, requesterId))
                                .thenApply(res -> true);
                    } else
                        return CompletableFuture.completedFuture(false);
                });
    }

    public static <T, O> CompletableFuture<QueryResults<T, O>> queryWithPaging(
            BiFunction<O, Integer, CompletableFuture<QueryResults<T, O>>> fn, O offset, int limit, int iterationsLeft) {
        if (iterationsLeft == 0)
            return CompletableFuture.completedFuture(new QueryResults<>(new ArrayList<>(), true, null, null));

        return fn.apply(offset, limit)
                .thenCompose(queryResults -> {
                    // if the results are less than the limit and we haven't reached the end...
                    if (queryResults.results.size() < limit && !queryResults.reachedEnd) {
                        O nextOffset;
                        int nextLimit = limit - queryResults.results.size();
                        if (queryResults.offset != null)
                            nextOffset = queryResults.offset;
                        else
                            return CompletableFuture.completedFuture(queryResults);
                        // recursively make the new request and concat the results.
                        return queryWithPaging(fn, nextOffset, nextLimit, iterationsLeft - 1)
                                .thenApply(nextResults -> {
                                    List<T> results = new ArrayList<>(queryResults.results);
                                    results.addAll(nextResults.results);
                                    return new QueryResults<>(results, nextResults.reachedEnd, nextResults.offset,
                                            nextResults.linkHeaderParams);
                                });
                    } else
                        return CompletableFuture.completedFuture(queryResults);
                });
    }

    public CompletableFuture<QueryResults<AccountWithId, Long>> getBlocks(long blockerId, Long offsetMaybe,
            Integer limitMaybe) {
        return queryWithPaging(
                (offset, limit) -> {
                    SortedRangeFromOptions options = SortedRangeFromOptions.excludeStart().maxAmt(limit);
                    CompletableFuture<List<Long>> blockeeIdsFuture = accountIdToSuppressions
                            .selectAsync(Path.key(blockerId, "blocked").sortedSetRangeFrom(offset, options).all());
                    return blockeeIdsFuture.thenCompose(this::getAccountsFromAccountIds)
                            .thenApply(accountWithIds -> {
                                Long lastId = null;
                                List<SimpleEntry<String, String>> linkHeaderParams = null;
                                if (accountWithIds.size() > 0) {
                                    lastId = accountWithIds.get(accountWithIds.size() - 1).accountId;
                                    linkHeaderParams = Arrays.asList(
                                            new SimpleEntry<>("max_id", ApolloHelpers.serializeAccountId(lastId)));
                                }
                                return new QueryResults<>(accountWithIds, accountWithIds.size() < limit, lastId,
                                        linkHeaderParams);
                            });
                },
                offsetMaybe == null ? -1L : offsetMaybe,
                Math.min(limitMaybe == null ? DEFAULT_LIMIT : limitMaybe, MAX_LIMIT),
                MAX_PAGING_ITERATIONS);
    }

    public CompletableFuture<StatusQueryResult> postLikeStatus(long likerId, StatusPointer pointer) {
        return likeStatusDepot.appendAsync(new LikeStatus(likerId, pointer, System.currentTimeMillis()))
                .thenCompose(res -> this.getStatus(likerId, pointer))
                .thenApply(resultMaybe -> {
                    if (resultMaybe == null)
                        return null;
                    // the change was processed in a microbatch
                    // so the query won't necessarily return the
                    // most up-to-date value, so we're updating it manually.
                    StatusQueryResult statusQueryResult = (StatusQueryResult) resultMaybe;
                    statusQueryResult.result.status.metadata.liked = true;
                    return statusQueryResult;
                });
    }

    public CompletableFuture<StatusQueryResult> postRemoveLikeStatus(long likerId, StatusPointer pointer) {
        return likeStatusDepot.appendAsync(new LikeStatus(likerId, pointer, System.currentTimeMillis()))
                .thenCompose(res -> this.getStatus(likerId, pointer))
                .thenApply(resultMaybe -> {
                    if (resultMaybe == null)
                        return null;
                    // the change was processed in a microbatch
                    // so the query won't necessarily return the
                    // most up-to-date value, so we're updating it manually.
                    StatusQueryResult statusQueryResult = (StatusQueryResult) resultMaybe;
                    statusQueryResult.result.status.metadata.liked = false;
                    return statusQueryResult;
                });
    }

    public CompletableFuture<StatusQueryResult> postBoostStatus(long boosterId, StatusPointer pointer) {
        BoostStatus boostStatus = new BoostStatus(UUID.randomUUID().toString(), boosterId, pointer,
                System.currentTimeMillis());
        return statusDepot.appendAsync(boostStatus)
                .thenCompose(res -> this.getStatus(boosterId, pointer))
                .thenApply(resultMaybe -> {
                    if (resultMaybe == null)
                        return null;
                    // the change was processed in a microbatch
                    // so the query won't necessarily return the
                    // most up-to-date value, so we're updating it manually.
                    StatusQueryResult statusQueryResult = (StatusQueryResult) resultMaybe;
                    statusQueryResult.result.status.metadata.boosted = true;
                    return statusQueryResult;
                });
    }

    public CompletableFuture<StatusQueryResult> postRemoveBoostStatus(long boosterId, StatusPointer pointer) {
        return statusDepot.appendAsync(new RemoveBoostStatus(boosterId, pointer, System.currentTimeMillis()))
                .thenCompose(res -> this.getStatus(boosterId, pointer))
                .thenApply(resultMaybe -> {
                    if (resultMaybe == null)
                        return null;
                    // the change was processed in a microbatch
                    // so the query won't necessarily return the
                    // most up-to-date value, so we're updating it manually.
                    StatusQueryResult statusQueryResult = (StatusQueryResult) resultMaybe;
                    statusQueryResult.result.status.metadata.boosted = false;
                    return statusQueryResult;
                });
    }

    public CompletableFuture<StatusQueryResult> postBookmarkStatus(long bookmarkerId, StatusPointer pointer) {
        return bookmarkStatusDepot.appendAsync(new BookmarkStatus(bookmarkerId, pointer, System.currentTimeMillis()))
                .thenCompose(res -> this.getStatus(bookmarkerId, pointer))
                .thenApply(resultMaybe -> {
                    if (resultMaybe == null)
                        return null;
                    // the change was processed in a microbatch
                    // so the query won't necessarily return the
                    // most up-to-date value, so we're updating it manually.
                    StatusQueryResult statusQueryResult = (StatusQueryResult) resultMaybe;
                    statusQueryResult.result.status.metadata.bookmarked = true;
                    return statusQueryResult;
                });
    }

    public CompletableFuture<StatusQueryResult> postRemoveBookmarkStatus(long bookmarkerId, StatusPointer pointer) {
        return bookmarkStatusDepot
                .appendAsync(new RemoveBookmarkStatus(bookmarkerId, pointer, System.currentTimeMillis()))
                .thenCompose(res -> this.getStatus(bookmarkerId, pointer))
                .thenApply(resultMaybe -> {
                    if (resultMaybe == null)
                        return null;
                    // the change was processed in a microbatch
                    // so the query won't necessarily return the
                    // most up-to-date value, so we're updating it manually.
                    StatusQueryResult statusQueryResult = (StatusQueryResult) resultMaybe;
                    statusQueryResult.result.status.metadata.bookmarked = false;
                    return statusQueryResult;
                });
    }

    public CompletableFuture<StatusQueryResult> postMuteStatus(long muterId, StatusPointer pointer) {
        return muteStatusDepot.appendAsync(new MuteStatus(muterId, pointer, System.currentTimeMillis()))
                .thenCompose(res -> this.getStatus(muterId, pointer))
                .thenApply(resultMaybe -> {
                    if (resultMaybe == null)
                        return null;
                    // the change was processed in a microbatch
                    // so the query won't necessarily return the
                    // most up-to-date value, so we're updating it manually.
                    StatusQueryResult statusQueryResult = (StatusQueryResult) resultMaybe;
                    statusQueryResult.result.status.metadata.muted = true;
                    return statusQueryResult;
                });
    }

    public CompletableFuture<StatusQueryResult> postRemoveMuteStatus(long muterId, StatusPointer pointer) {
        return muteStatusDepot.appendAsync(new RemoveMuteStatus(muterId, pointer, System.currentTimeMillis()))
                .thenCompose(res -> this.getStatus(muterId, pointer))
                .thenApply(resultMaybe -> {
                    if (resultMaybe == null)
                        return null;
                    // the change was processed in a microbatch
                    // so the query won't necessarily return the
                    // most up-to-date value, so we're updating it manually.
                    StatusQueryResult statusQueryResult = (StatusQueryResult) resultMaybe;
                    statusQueryResult.result.status.metadata.muted = false;
                    return statusQueryResult;
                });
    }

    public CompletableFuture<StatusQueryResult> postPinStatus(long pinnerId, StatusPointer pointer) {
        return pinStatusDepot.appendAsync(new PinStatus(pinnerId, pointer.statusId, System.currentTimeMillis()))
                .thenCompose(res -> this.getStatus(pinnerId, pointer))
                .thenApply(resultMaybe -> {
                    if (resultMaybe == null)
                        return null;
                    // the change was processed in a microbatch
                    // so the query won't necessarily return the
                    // most up-to-date value, so we're updating it manually.
                    StatusQueryResult statusQueryResult = (StatusQueryResult) resultMaybe;
                    statusQueryResult.result.status.metadata.pinned = true;
                    return statusQueryResult;
                });
    }

    public CompletableFuture<StatusQueryResult> postRemovePinStatus(long pinnerId, StatusPointer pointer) {
        return pinStatusDepot.appendAsync(new RemovePinStatus(pinnerId, pointer.statusId, System.currentTimeMillis()))
                .thenCompose(res -> this.getStatus(pinnerId, pointer))
                .thenApply(resultMaybe -> {
                    if (resultMaybe == null)
                        return null;
                    // the change was processed in a microbatch
                    // so the query won't necessarily return the
                    // most up-to-date value, so we're updating it manually.
                    StatusQueryResult statusQueryResult = (StatusQueryResult) resultMaybe;
                    statusQueryResult.result.status.metadata.pinned = false;
                    return statusQueryResult;
                });
    }

    public CompletableFuture<StatusQueryResult> putStatus(StatusPointer statusPointer, PutStatus params) {
        List<CompletableFuture<Object>> mediaFutures = params.media_ids.stream()
                .distinct()
                .map(attachmentId -> uuidToAttachment.selectOneAsync(Path.key(attachmentId)))
                .collect(Collectors.toList());
        return CompletableFuture.allOf(mediaFutures.toArray(new CompletableFuture<?>[0]))
                .thenCompose(_result -> {
                    List<AttachmentWithId> attachments = new ArrayList<>();
                    for (int i = 0; i < mediaFutures.size(); i++) {
                        attachments.add(
                                new AttachmentWithId(params.media_ids.get(i), (Attachment) mediaFutures.get(i).join()));
                    }
                    return accountIdToStatuses
                            .selectOneAsync(Path.key(statusPointer.authorId, statusPointer.statusId).first())
                            .thenCompose(statusMaybe -> {
                                if (statusMaybe == null)
                                    return CompletableFuture.completedFuture(null);
                                Status edit = (Status) statusMaybe;
                                if (edit.content.isSetNormal()) {
                                    NormalStatusContent content = edit.content.getNormal();
                                    content.text = params.status;
                                    content.setAttachments(attachments);
                                    if (params.poll != null && content.isSetPollContent())
                                        content.setPollContent(new PollContent(params.poll.options,
                                                content.pollContent.expirationMillis, params.poll.multiple));
                                    if (params.sensitive != null && params.sensitive)
                                        content.setSensitiveWarning(
                                                params.spoiler_text != null ? params.spoiler_text : "");
                                    else
                                        content.unsetSensitiveWarning();
                                } else if (edit.content.isSetReply()) {
                                    ReplyStatusContent content = edit.content.getReply();
                                    content.text = params.status;
                                    content.setAttachments(attachments);
                                    if (params.poll != null && content.isSetPollContent())
                                        content.setPollContent(new PollContent(params.poll.options,
                                                content.pollContent.expirationMillis, params.poll.multiple));
                                    if (params.sensitive != null && params.sensitive)
                                        content.setSensitiveWarning(
                                                params.spoiler_text != null ? params.spoiler_text : "");
                                    else
                                        content.unsetSensitiveWarning();
                                } else if (edit.content.isSetBoost())
                                    return CompletableFuture.completedFuture(null); // you can't edit boosts
                                return statusDepot.appendAsync(new EditStatus(statusPointer.statusId, edit))
                                        .thenCompose(res -> this.getStatus(statusPointer.authorId, statusPointer));
                            });
                });
    }

    public CompletableFuture<Boolean> deleteStatus(long accountId, long statusId) {
        return statusDepot.appendAsync(new RemoveStatus(accountId, statusId, System.currentTimeMillis()))
                .thenApply(res -> true);
    }

    public CompletableFuture<StatusQueryResults> getAncestors(Long requestAccountIdMaybe, StatusPointer pointer) {
        return getAncestors.invokeAsync(requestAccountIdMaybe, pointer.authorId, pointer.statusId, ANCESTORS_LIMIT);
    }

    public CompletableFuture<StatusQueryResults> getDescendants(Long requestAccountIdMaybe, StatusPointer pointer) {
        return getDescendants.invokeAsync(requestAccountIdMaybe, pointer.authorId, pointer.statusId, DESCENDANTS_LIMIT);
    }

    public CompletableFuture<StatusQueryResults> getBookmarks(long bookmarkerId, StatusPointer offsetMaybe,
            Integer limitMaybe) {
        return queryStatusesWithPaging((offset, limit) -> {
            SortedRangeFromOptions options = SortedRangeFromOptions.excludeStart().maxAmt(limit);
            return bookmarkerToStatusPointers
                    .selectAsync(Path.key(bookmarkerId).sortedMapRangeFrom(offset, options).mapKeys())
                    .thenCompose(statusPointers -> {
                        QueryFilterOptions filterOptions = new QueryFilterOptions(FilterContext.Public, false);
                        return getStatusesFromPointers.invokeAsync(bookmarkerId, statusPointers, filterOptions);
                    });
        }, offsetMaybe, limitMaybe, MAX_PAGING_ITERATIONS);
    }

    public CompletableFuture<StatusQueryResults> getLikes(long likerId, StatusPointer offsetMaybe, Integer limitMaybe) {
        return queryStatusesWithPaging((offset, limit) -> {
            SortedRangeFromOptions options = SortedRangeFromOptions.excludeStart().maxAmt(limit);
            return likerToStatusPointers.selectAsync(Path.key(likerId).sortedMapRangeFrom(offset, options).mapKeys())
                    .thenCompose(statusPointers -> {
                        QueryFilterOptions filterOptions = new QueryFilterOptions(FilterContext.Public, false);
                        return getStatusesFromPointers.invokeAsync(likerId, statusPointers, filterOptions);
                    });
        }, offsetMaybe, limitMaybe, MAX_PAGING_ITERATIONS);
    }

    public CompletableFuture<QueryResults<AccountWithId, Long>> getStatusBoosters(Long requestAccountIdMaybe,
            long authorId, long statusId, Long offsetMaybe, Integer limitMaybe) {
        long offset = offsetMaybe == null ? -1L : offsetMaybe;
        int defaultLimit = 40;
        int maxLimit = 80;
        int limit = Math.min(limitMaybe == null ? defaultLimit : limitMaybe, maxLimit);
        // make sure requester is allowed to see status
        return this.getStatus(requestAccountIdMaybe, new StatusPointer(authorId, statusId))
                .thenCompose(resultMaybe -> {
                    if (resultMaybe == null)
                        return CompletableFuture.completedFuture(null);
                    // get the boosters
                    SortedRangeFromOptions options = SortedRangeFromOptions.excludeStart().maxAmt(limit);
                    CompletableFuture<List<Long>> boosterIdsFuture = statusIdToBoosters.selectAsync(authorId,
                            Path.key(statusId).sortedMapRangeFrom(offset, options).mapKeys());
                    return boosterIdsFuture.thenCompose(this::getAccountsFromAccountIds)
                            .thenApply(accountWithIds -> {
                                Long lastId = null;
                                List<SimpleEntry<String, String>> linkHeaderParams = null;
                                if (accountWithIds.size() > 0) {
                                    lastId = accountWithIds.get(accountWithIds.size() - 1).accountId;
                                    linkHeaderParams = Arrays.asList(
                                            new SimpleEntry<>("max_id", ApolloHelpers.serializeAccountId(lastId)));
                                }
                                return new QueryResults<>(accountWithIds, accountWithIds.size() < limit, lastId,
                                        linkHeaderParams);
                            });
                });
    }

    public CompletableFuture<QueryResults<AccountWithId, Long>> getStatusLikers(Long requestAccountIdMaybe,
            long authorId, long statusId, Long offsetMaybe, Integer limitMaybe) {
        long offset = offsetMaybe == null ? -1L : offsetMaybe;
        int defaultLimit = 40;
        int maxLimit = 80;
        int limit = Math.min(limitMaybe == null ? defaultLimit : limitMaybe, maxLimit);
        // make sure requester is allowed to see status
        return this.getStatus(requestAccountIdMaybe, new StatusPointer(authorId, statusId))
                .thenCompose(resultMaybe -> {
                    if (resultMaybe == null)
                        return CompletableFuture.completedFuture(null);
                    // get the likers
                    SortedRangeFromOptions options = SortedRangeFromOptions.excludeStart().maxAmt(limit);
                    CompletableFuture<List<Long>> likerIdsFuture = statusIdToLikers.selectAsync(authorId,
                            Path.key(statusId).sortedMapRangeFrom(offset, options).mapKeys());
                    return likerIdsFuture.thenCompose(this::getAccountsFromAccountIds)
                            .thenApply(accountWithIds -> {
                                Long lastId = null;
                                List<SimpleEntry<String, String>> linkHeaderParams = null;
                                if (accountWithIds.size() > 0) {
                                    AccountWithId lastAccount = accountWithIds.get(accountWithIds.size() - 1);
                                    lastId = lastAccount.accountId;
                                    linkHeaderParams = Arrays.asList(new SimpleEntry<>("max_id",
                                            ApolloHelpers.serializeAccountId(lastAccount.accountId)));
                                }
                                return new QueryResults<>(accountWithIds, accountWithIds.size() < limit, lastId,
                                        linkHeaderParams);
                            });
                });
    }

    public CompletableFuture<QueryResults<AccountWithId, Map>> getProfileSearch(long requestAccountId,
            List<String> terms, Map startParamsMaybe, Integer limitMaybe, boolean followeesOnly) {
        int defaultLimit = 40;
        int maxLimit = 80;
        return queryWithPaging(
                (offset, limit) -> {
                    CompletableFuture<Map> matchListFuture = profileTermsSearch.invokeAsync(terms, offset, limit);
                    return matchListFuture.thenCompose(result -> {
                        Map nextParams = ApolloApiHelpers.createSearchParams(result);
                        List<String> matchList = (List<String>) result.get("matchList");
                        return getAccountsFromNames.invokeAsync(requestAccountId, matchList)
                                .thenApply(accountWithIds -> {
                                    if (followeesOnly)
                                        accountWithIds = accountWithIds.stream()
                                                .filter(o -> o.metadata.isFollowedByRequester)
                                                .collect(Collectors.toList());
                                    return new QueryResults<>(accountWithIds, nextParams == null, nextParams,
                                            ApolloApiHelpers.createLinkHeaderParams(nextParams));
                                });
                    });
                },
                startParamsMaybe,
                Math.min(limitMaybe == null ? defaultLimit : limitMaybe, maxLimit),
                MAX_PAGING_ITERATIONS)
                .thenCompose(result -> {
                    if (terms.size() == 1 && result.results.isEmpty()) {
                        // override prefix search
                        List<String> terms2 = Arrays.asList(terms.get(0), terms.get(0));
                        return getProfileSearch(requestAccountId, terms2, startParamsMaybe, limitMaybe, followeesOnly);
                    } else {
                        // deduplicate results
                        LinkedHashMap<Long, AccountWithId> dedupedResults = new LinkedHashMap<>();
                        for (AccountWithId awid : result.results)
                            dedupedResults.put(awid.accountId, awid);
                        result.results = new ArrayList<>(dedupedResults.values());
                        return CompletableFuture.completedFuture(result);
                    }
                });
    }

    public CompletableFuture<Boolean> postEditAccount(long accountId, List<EditAccountField> edits) {
        if (edits.size() == 0)
            return CompletableFuture.completedFuture(true);
        return accountEditDepot.appendAsync(new EditAccount(accountId, edits, System.currentTimeMillis()))
                .thenApply(res -> true);
    }

    public CompletableFuture<List<AccountWithId>> getFamiliarFollowers(long requestAccountId, long targetId) {
        CompletableFuture<List<Long>> familiarFollowersFuture = getFamiliarFollowers.invokeAsync(requestAccountId,
                targetId);
        return familiarFollowersFuture.thenCompose(this::getAccountsFromAccountIds);
    }

    public CompletableFuture<StatusQueryResults> getAttachmentStatuses(Long requestAccountIdMaybe, long authorId,
            StatusPointer offsetMaybe, Integer limitMaybe) {
        return queryStatusesWithPaging((offset, limit) -> {
            SortedRangeFromOptions options = SortedRangeFromOptions.excludeStart().maxAmt(limit);
            return accountIdToAttachmentStatusIds
                    .selectAsync(Path.key(authorId).sortedSetRangeFrom(offset.statusId, options).all())
                    .thenCompose(statusIds -> {
                        List<StatusPointer> pointers = new ArrayList<>();
                        for (Object statusId : statusIds)
                            pointers.add(new StatusPointer(authorId, (Long) statusId));
                        QueryFilterOptions filterOptions = new QueryFilterOptions(FilterContext.Public, false);
                        return getStatusesFromPointers.invokeAsync(requestAccountIdMaybe, pointers, filterOptions);
                    });
        }, offsetMaybe, limitMaybe, MAX_PAGING_ITERATIONS);
    }

    public CompletableFuture<StatusQueryResults> getTaggedStatuses(Long requestAccountIdMaybe, long authorId,
            String hashtag, StatusPointer offsetMaybe, Integer limitMaybe) {
        return queryStatusesWithPaging((offset, limit) -> {
            SortedRangeFromOptions rangeOptions = SortedRangeFromOptions.excludeStart().maxAmt(limit);
            return accountIdToHashtagActivity
                    .selectAsync(Path.key(authorId, hashtag, "timeline")
                            .sortedSetRangeFrom(offset.statusId, rangeOptions).all())
                    .thenCompose((List<Object> statusIds) -> {
                        QueryFilterOptions filterOptions = new QueryFilterOptions(FilterContext.Public, false);
                        List<StatusPointer> pointers = statusIds.stream()
                                .map((statusId) -> new StatusPointer(authorId, (Long) statusId))
                                .collect(Collectors.toList());
                        return getStatusesFromPointers.invokeAsync(requestAccountIdMaybe, pointers, filterOptions);
                    });
        }, offsetMaybe, limitMaybe, MAX_PAGING_ITERATIONS);
    }

    public CompletableFuture<QueryResults<GetNotification.Bundle, Long>> getNotificationsTimeline(long accountId,
            Long offsetMaybe, Integer limitMaybe, List<String> typesMaybe, List<String> excludeTypesMaybe) {
        int defaultLimit = 15;
        int maxLimit = 30;
        Set<String> types = new HashSet<>();
        if (typesMaybe != null)
            types.addAll(typesMaybe);
        Set<String> excludeTypes = new HashSet<>();
        if (excludeTypesMaybe != null)
            excludeTypes.addAll(excludeTypesMaybe);
        return queryWithPaging(
                (offset, limit) -> {
                    SortedRangeFromOptions options = SortedRangeFromOptions.excludeStart().maxAmt(limit);
                    CompletableFuture<List<List>> notificationsFuture = accountIdToNotificationsTimeline
                            .selectAsync(Path.key(accountId).sortedMapRangeFrom(offset, options).all());
                    return notificationsFuture.thenCompose(timelineIndexAndNotifications -> {
                        // create notifications and filter them
                        List<NotificationWithId> notificationWithIds = ApolloHelpers
                                .createNotificationWithIds(timelineIndexAndNotifications);
                        List<NotificationWithId> filtered = notificationWithIds.stream()
                                .filter(nwid -> types.contains(
                                        ApolloHelpers.getTypeFromNotificationContent(nwid.notification.content)))
                                .filter(nwid -> !excludeTypes.contains(
                                        ApolloHelpers.getTypeFromNotificationContent(nwid.notification.content)))
                                .collect(Collectors.toList());
                        // get any accounts/statuses associated with the notifications
                        List<CompletableFuture<GetNotification.Bundle>> bundleFutures = filtered.stream()
                                .map(notificationWithId -> this.getNotification(accountId, notificationWithId))
                                .collect(Collectors.toList());
                        return CompletableFuture.allOf(bundleFutures.toArray(new CompletableFuture<?>[0]))
                                .thenApply(_result -> {
                                    // filter out the nulls. if a bundle null,
                                    // the user that generated the notification is blocked/muted
                                    // or the status it pointed to is gone.
                                    List<GetNotification.Bundle> bundles = new ArrayList<>();
                                    for (CompletableFuture<GetNotification.Bundle> bundleFuture : bundleFutures) {
                                        GetNotification.Bundle bundle = bundleFuture.join();
                                        if (bundle != null)
                                            bundles.add(bundle);
                                    }
                                    Long lastId = null;
                                    List<SimpleEntry<String, String>> linkHeaderParams = null;
                                    if (notificationWithIds.size() > 0) {
                                        NotificationWithId lastNotification = notificationWithIds
                                                .get(notificationWithIds.size() - 1);
                                        lastId = lastNotification.notificationId;
                                        linkHeaderParams = Arrays.asList(new SimpleEntry<>("max_id",
                                                ApolloHelpers.serializeNotificationId(lastNotification.notificationId,
                                                        lastNotification.notification.timestamp)));
                                    }
                                    return new QueryResults<>(bundles, notificationWithIds.size() < limit, lastId,
                                            linkHeaderParams);
                                });
                    });
                },
                offsetMaybe == null ? -1L : offsetMaybe,
                Math.min(limitMaybe == null ? defaultLimit : limitMaybe, maxLimit),
                MAX_PAGING_ITERATIONS);
    }

    public CompletableFuture<GetNotification.Bundle> getNotification(long requestAccountId,
            NotificationWithId notificationWithId) {
        // get account associated with the notification
        return this
                .getAccountWithId(
                        ApolloHelpers.getAccountIdFromNotificationContent(notificationWithId.notification.content))
                .thenCompose(accountWithId -> {
                    if (accountWithId == null)
                        return CompletableFuture.completedFuture(null);
                    // determine if requester is currently muting this account's notifications
                    CompletableFuture<MuteAccountOptions> optionsFuture = accountIdToSuppressions
                            .selectOneAsync(Path.key(requestAccountId, "muted", accountWithId.accountId));
                    return optionsFuture.thenCompose(muteAccountOptions -> {
                        if (muteAccountOptions != null && muteAccountOptions.muteNotifications)
                            return CompletableFuture.completedFuture(null);
                        // get status associated with the notification
                        StatusPointer statusPointer = ApolloHelpers
                                .getStatusPointerFromNotificationContent(notificationWithId.notification.content);
                        if (statusPointer == null) {
                            return CompletableFuture.completedFuture(
                                    new GetNotification.Bundle(notificationWithId, accountWithId, null));
                        } else {
                            QueryFilterOptions filterOptions = new QueryFilterOptions(FilterContext.Notifications,
                                    true);
                            return this.getStatus(requestAccountId, statusPointer, filterOptions)
                                    .thenApply(statusQueryResult -> {
                                        if (statusQueryResult == null)
                                            return null;
                                        return new GetNotification.Bundle(notificationWithId, accountWithId,
                                                statusQueryResult);
                                    });
                        }
                    });
                });
    }

    public CompletableFuture<GetNotification.Bundle> getNotification(long accountId, long notificationId) {
        return accountIdToNotificationsTimeline.selectOneAsync(Path.key(accountId, notificationId))
                .thenCompose(notification -> {
                    if (notification == null)
                        return null;
                    NotificationWithId notificationWithId = new NotificationWithId(notificationId,
                            (Notification) notification);
                    return this.getNotification(accountId, notificationWithId);
                });
    }

    public CompletableFuture<Boolean> dismissNotification(long accountId, Long notificationIdMaybe) {
        DismissNotification dismissNotification = new DismissNotification(accountId);
        if (notificationIdMaybe != null)
            dismissNotification.setNotificationId(notificationIdMaybe);
        return dismissDepot.appendAsync(dismissNotification).thenApply(res -> true);
    }

    public CompletableFuture<Boolean> postPollVote(long accountId, StatusPointer pointer, Set<Integer> choices) {
        return pollVoteDepot.appendAsync(new PollVote(accountId, pointer, choices, System.currentTimeMillis()))
                .thenApply(res -> true);
    }

    public CompletableFuture<List<AccountWithId>> getDirectory(boolean showAll, boolean sortByActive,
            Integer limitMaybe, Integer offsetMaybe) {
        // Determine the offset and limit
        long offset = offsetMaybe == null ? 0 : offsetMaybe;
        int defaultLimit = 40;
        int maxLimit = 80;
        int limit = Math.min(limitMaybe == null ? defaultLimit : limitMaybe, maxLimit);

        // Determine which account ID set to use based on the parameters
        CompletableFuture<List<Long>> future = (sortByActive ? activeAccountIds : newAccountIds)
                .selectAsync(Path.all())
                .thenApply(results -> results.stream()
                        // sort by timestamp (descending) and then return the account ids
                        .sorted((o1, o2) -> ((List<Long>) o2).get(1).compareTo(((List<Long>) o1).get(1)))
                        .map(result -> ((List<Long>) result).get(0))
                        .collect(Collectors.toList()));

        // Process the resulting IDs
        return future.thenApply(results -> {
            List<Long> accountIds = new ArrayList<>();
            Map<Long, Integer> accountIdToIndex = new HashMap<>();
            long count = 0;
            for (Long accountId : results) {
                if (count == offset + limit)
                    break;
                // remove existing account id if necessary
                Integer existingIndex = accountIdToIndex.get(accountId);
                if (existingIndex != null)
                    accountIds.set(existingIndex, null);
                else
                    count += 1;
                // add account id
                accountIdToIndex.put(accountId, accountIds.size());
                accountIds.add(accountId);
            }
            return accountIds.stream().filter(Objects::nonNull).skip(offset).collect(Collectors.toList());
        }).thenCompose(this::getAccountsFromAccountIds);
    }

    public CompletableFuture<FilterWithId> postFilter(Filter filter) {
        String uuid = UUID.randomUUID().toString();
        AddFilter addFilter = new AddFilter(filter, uuid);
        return filterDepot.appendAsync(addFilter)
                .thenCompose(res -> postUUIDToGeneratedId.selectOneAsync(Path.key(uuid)))
                .thenCompose(
                        filterId -> accountIdToFilterIdToFilter.selectOneAsync(Path.key(filter.accountId, filterId))
                                .thenApply(foundFilter -> new FilterWithId((long) filterId, (Filter) foundFilter)));
    }

    public CompletableFuture<List<FilterWithId>> getFilters(Long requestAccountId) {
        return accountIdToFilterIdToFilter.selectAsync(Path.key(requestAccountId).all())
                .thenApply(result -> ApolloHelpers.createFiltersWithIds((List) result));
    }

    public CompletableFuture<FilterWithId> getFilter(Long accountId, Long filterId) {
        return accountIdToFilterIdToFilter.selectOneAsync(Path.key(accountId, filterId))
                .thenApply(result -> {
                    if (result == null)
                        return null;
                    return new FilterWithId(filterId, (Filter) result);
                });
    }

    public CompletableFuture<FilterWithId> putFilter(EditFilter edit) {
        return filterDepot.appendAsync(edit)
                .thenCompose(res -> accountIdToFilterIdToFilter.selectOneAsync(Path.key(edit.accountId, edit.filterId)))
                .thenApply(filter -> filter == null ? null : new FilterWithId(edit.filterId, (Filter) filter));
    }

    public CompletableFuture<Void> deleteFilter(Long accountId, Long filterId) {
        return filterDepot.appendAsync(new RemoveFilter(filterId, accountId, System.currentTimeMillis()))
                .thenApply(result -> null);
    }

    public CompletableFuture<AttachmentWithId> postAttachment(AttachmentWithId attachment) {
        return statusAttachmentWithIdDepot.appendAsync(attachment).thenApply(res -> attachment);
    }

    public CompletableFuture<AttachmentWithId> getAttachment(String uuid) {
        return uuidToAttachment.selectOneAsync(Path.key(uuid))
                .thenApply(attachment -> new AttachmentWithId(uuid, (Attachment) attachment));
    }

    public CompletableFuture<List<AccountWithId>> getWhoToFollowSuggestions(long accountId) {
        return getWhoToFollowSuggestions.invokeAsync(accountId)
                .thenApply(ArrayList::new)
                .thenCompose(this::getAccountsFromAccountIds);
    }

    public CompletableFuture<Boolean> removeFollowSuggestion(long accountId, long targetId) {
        return removeFollowSuggestionDepot.appendAsync(new RemoveFollowSuggestion(accountId, targetId))
                .thenApply(res -> true);
    }

    public CompletableFuture<ItemStats> getHashtagStats(String hashtag) {
        return batchHashtagStats.invokeAsync(Arrays.asList(hashtag)).thenApply(info -> info.get(hashtag));
    }

    public CompletableFuture<Boolean> postFollowHashtag(long accountId, String hashtag) {
        return followHashtagDepot.appendAsync(new FollowHashtag(accountId, hashtag, System.currentTimeMillis()))
                .thenApply(res -> true);
    }

    public CompletableFuture<Boolean> postRemoveFollowHashtag(long accountId, String hashtag) {
        return followHashtagDepot.appendAsync(new RemoveFollowHashtag(accountId, hashtag, System.currentTimeMillis()))
                .thenApply(res -> true);
    }

    public CompletableFuture<Boolean> isFollowingHashtag(long accountId, String hashtag) {
        return hashtagToFollowers.selectOneAsync(Path.key(hashtag).view(Ops.CONTAINS, accountId));
    }

    public CompletableFuture<StatusQueryResults> getHomeTimeline(long accountId, StatusPointer offsetMaybe,
            Integer limitMaybe) {
        return queryStatusesWithPaging((offset, limit) -> getHomeTimeline.invokeAsync(accountId, offset, limit),
                offsetMaybe, limitMaybe, MAX_PAGING_ITERATIONS);
    }

    public CompletableFuture<StatusQueryResults> getDirectTimeline(long accountId, StatusPointer offsetMaybe,
            Integer limitMaybe) {
        return queryStatusesWithPaging(
                (offset, limit) -> accountIdToDirectMessages.selectOneAsync(Path.key(accountId, offset).nullToVal(-1L))
                        .thenCompose(timelineIndex -> getDirectTimeline.invokeAsync(accountId, timelineIndex, limit)),
                offsetMaybe, limitMaybe, MAX_PAGING_ITERATIONS);
    }

    public CompletableFuture<StatusQueryResults> getLocalTimeline(LocalTimeline timelineType,
            Long requestAccountIdMaybe, StatusPointer offsetMaybe, Integer limitMaybe) {
        // unlike other timelines, this one is queried entirely from an in-memory cache.
        // this is because the global timeline is the same for everyone, so querying the
        // backend every time would be wasteful. if the user is logged in, we still need
        // to query, to ensure that their blocks/mutes are accounted for in the results.
        return queryStatusesWithPaging((offset, limit) -> {
            long timelineIndex = ApolloApiStreamingConfig.LOCAL_TIMELINE_TO_STATUS_POINTER_TO_INDEX.get(timelineType)
                    .getOrDefault(offset, -1L);
            SortedMap<Long, StatusQueryResult> submap = ApolloApiStreamingConfig.LOCAL_TIMELINE_TO_INDEX_TO_STATUS
                    .get(timelineType).tailMap(timelineIndex, false);
            // if not logged in, return results entirely from cache
            if (requestAccountIdMaybe == null) {
                List<StatusPointer> statusPointers = new ArrayList<>();
                List<StatusResultWithId> results = new ArrayList<>();
                Map<String, AccountWithId> mentions = new HashMap<>();
                for (Map.Entry<Long, StatusQueryResult> entry : submap.entrySet()) {
                    StatusQueryResult r = entry.getValue();
                    statusPointers.add(new StatusPointer(r.result.status.author.accountId, r.result.statusId));
                    results.add(r.result);
                    mentions.putAll(r.mentions);
                    if (statusPointers.size() == limit)
                        break;
                }
                StatusQueryResults statusQueryResults = new StatusQueryResults(results, mentions, false, false);
                ApolloHelpers.updateStatusQueryResults(statusQueryResults, statusPointers, limit, false);
                return CompletableFuture.completedFuture(statusQueryResults);
            }
            // if logged in, get status pointers from cache and then query the backend for
            // the full results.
            // this ensures that blocks/mutes are taken into account.
            else {
                List<StatusPointer> statusPointers = new ArrayList<>();
                for (Map.Entry<Long, StatusQueryResult> entry : submap.entrySet()) {
                    StatusQueryResult r = entry.getValue();
                    statusPointers.add(new StatusPointer(r.result.status.author.accountId, r.result.statusId));
                    if (statusPointers.size() == limit)
                        break;
                }
                QueryFilterOptions filterOptions = new QueryFilterOptions(FilterContext.Public, true);
                return getStatusesFromPointers.invokeAsync(requestAccountIdMaybe, statusPointers, filterOptions)
                        .thenApply(statusQueryResults -> ApolloHelpers.updateStatusQueryResults(statusQueryResults,
                                statusPointers, limit, false));
            }
        },
                offsetMaybe, limitMaybe, MAX_PAGING_ITERATIONS);
    }

    public void resolveStatusPointers(Map<StatusPointer, Long> statusPointerToIndex,
            Map<Long, StatusQueryResult> indexToStatus) {
        // query the statuses
        List<StatusPointer> statusPointers = new ArrayList<>(statusPointerToIndex.keySet());
        QueryFilterOptions filterOptions = new QueryFilterOptions(FilterContext.Public, false);
        StatusQueryResults statusQueryResults = getStatusesFromPointers.invoke(null, statusPointers, filterOptions);
        // make a new sorted map with the results in it
        for (StatusResultWithId result : statusQueryResults.results) {
            Long timelineIndex = statusPointerToIndex
                    .get(new StatusPointer(result.status.author.accountId, result.statusId));
            if (timelineIndex != null)
                indexToStatus.put(timelineIndex, new StatusQueryResult(result, statusQueryResults.mentions));
        }
    }

    public CompletableFuture<StatusQueryResults> getHashtagTimeline(String hashtag, Long requestAccountIdMaybe,
            StatusPointer offsetMaybe, Integer limitMaybe) {
        return queryStatusesWithPaging(
                (offset, limit) -> hashtagToStatusPointersReverse
                        .selectOneAsync(Path.key(hashtag, offset).nullToVal(-1L))
                        .thenCompose(timelineIndex -> getHashtagTimeline.invokeAsync(hashtag, requestAccountIdMaybe,
                                timelineIndex, limit)),
                offsetMaybe, limitMaybe, MAX_PAGING_ITERATIONS);
    }

    public CompletableFuture<QueryResults<StatusQueryResult, Map>> getStatusSearch(long requestAccountId,
            Long authorIdMaybe, List<String> terms, Map startParamsMaybe, Integer limitMaybe) {
        int defaultLimit = 40;
        int maxLimit = 80;
        return queryWithPaging(
                (offset, limit) -> {
                    CompletableFuture<Map> matchListFuture = statusTermsSearch.invokeAsync(
                            authorIdMaybe != null ? authorIdMaybe : requestAccountId, terms, offset, limit);
                    return matchListFuture.thenCompose(result -> {
                        Map nextParams = ApolloApiHelpers.createSearchParams(result);
                        List<StatusPointer> matchList = ((List<List>) result.get("matchList")).stream()
                                .map(pair -> new StatusPointer((Long) pair.get(0), (Long) pair.get(1)))
                                .collect(Collectors.toList());
                        return getStatusesFromPointers
                                .invokeAsync(requestAccountId, matchList,
                                        new QueryFilterOptions(FilterContext.Public, false))
                                .thenApply(statusQueryResults -> {
                                    List<StatusQueryResult> filtered = new ArrayList<>();
                                    for (StatusResultWithId sqr : statusQueryResults.results) {
                                        // if authorIdMaybe is set, we are searching for only a particular user's
                                        // statuses.
                                        // in that case, only include results written by that user (i.e. filter out
                                        // mentions)
                                        if (authorIdMaybe == null || sqr.status.author.accountId == authorIdMaybe)
                                            filtered.add(new StatusQueryResult(sqr, statusQueryResults.mentions));
                                    }
                                    return new QueryResults<>(filtered, nextParams == null, nextParams,
                                            ApolloApiHelpers.createLinkHeaderParams(nextParams));
                                });
                    });
                },
                startParamsMaybe,
                Math.min(limitMaybe == null ? defaultLimit : limitMaybe, maxLimit),
                MAX_PAGING_ITERATIONS);
    }

    public CompletableFuture<QueryResults<SimpleEntry<String, ItemStats>, Map>> getHashtagSearch(String term,
            Map startParamsMaybe, Integer limitMaybe) {
        int defaultLimit = 40;
        int maxLimit = 80;
        int limit = Math.min(limitMaybe == null ? defaultLimit : limitMaybe, maxLimit);
        CompletableFuture<Map> matchListFuture = hashtagSearch.invokeAsync(term, startParamsMaybe, limit);
        return matchListFuture.thenCompose(result -> {
            Map nextParams = ApolloApiHelpers.createSearchParams(result);
            List<String> matchList = (List<String>) result.get("matchList");
            return batchHashtagStats.invokeAsync(matchList.stream().distinct().collect(Collectors.toList()))
                    .thenApply(hashtagToStats -> {
                        if (hashtagToStats == null)
                            hashtagToStats = new HashMap<>();
                        List<SimpleEntry<String, ItemStats>> results = new ArrayList<>();
                        for (Map.Entry<String, ItemStats> entry : hashtagToStats.entrySet()) {
                            results.add(new SimpleEntry<>(entry.getKey(), entry.getValue()));
                        }
                        return new QueryResults<>(results, nextParams == null, nextParams,
                                ApolloApiHelpers.createLinkHeaderParams(nextParams));
                    });
        });
    }

    public CompletableFuture<Conversation> getConversationFromStatusId(long accountId, StatusPointer pointer) {
        return statusIdToConvoId
                .selectOneAsync(pointer.authorId, Path.key(pointer.statusId).nullToVal(pointer.statusId))
                .thenCompose(conversationId -> getConversation.invokeAsync(accountId, conversationId));
    }

    // reactive queries

    public static class HomeTimelineProxyState implements ProxyState<SortedMap> {
        public long accountId;
        public StatusPointer mostRecentStatusPointer;
        public ProxyState.Callback<SortedMap> callback;

        public HomeTimelineProxyState(long accountId, StatusPointer mostRecentStatusPointer,
                ProxyState.Callback<SortedMap> callback) {
            this.accountId = accountId;
            this.mostRecentStatusPointer = mostRecentStatusPointer;
            this.callback = callback;
        }

        @Override
        public SortedMap get() {
            throw new RuntimeException("Not implemented");
        }

        @Override
        public void close() throws IOException {
        }

        @Override
        public Status status() {
            // TODO Auto-generated method stub
            throw new UnsupportedOperationException("Unimplemented method 'status'");
        }
    }

    public void refreshHomeTimelineProxies(List<HomeTimelineProxyState> activeProxies) {
        List<List<HomeTimelineProxyState>> partitions = Lists.partition(activeProxies, 100);
        for (List<HomeTimelineProxyState> partition : partitions) {
            List tuples = new ArrayList();
            for (HomeTimelineProxyState p : partition)
                tuples.add(Arrays.asList(p.accountId, p.mostRecentStatusPointer));
            Map<Integer, List<StatusPointer>> res = getHomeTimelinesUntil.invoke(tuples, 50);
            for (int i = 0; i < partition.size(); i++) {
                HomeTimelineProxyState p = partition.get(i);
                List<StatusPointer> pointers = res.get(i);
                if (!pointers.isEmpty())
                    p.mostRecentStatusPointer = pointers.get(0);
                // diff processor doesn't need old/new values since it handles KeyDiff
                for (int j = pointers.size() - 1; j >= 0; j--)
                    p.callback.change(null, new KeyDiff((long) j, new NewValueDiff(pointers.get(j))), null);
            }
        }
    }

    public CompletableFuture<HomeTimelineProxyState> proxyHomeTimeline(long accountId,
            ProxyState.Callback<SortedMap> callback) {
        return getHomeTimelinesUntil.invokeAsync(Arrays.asList(Arrays.asList(accountId, new StatusPointer(-1, -1))), 1)
                .thenApply((Map<Integer, List<StatusPointer>> m) -> {
                    StatusPointer mostRecent = null;
                    if (!m.get(0).isEmpty())
                        mostRecent = m.get(0).get(0);
                    return new HomeTimelineProxyState(accountId, mostRecent, callback);
                });
    }

    public CompletableFuture<ProxyState<SortedMap>> proxyNotificationsTimeline(long accountId,
            ProxyState.Callback<SortedMap> callback) {
        return accountIdToNotificationsTimeline
                .proxyAsync(Path.key(accountId).sortedMapRangeFrom(0L, STREAM_QUERY_LIMIT), callback);
    }

    public CompletableFuture<ProxyState<SortedMap>> proxyHashtagTimeline(String hashtag,
            ProxyState.Callback<SortedMap> callback) {
        return hashtagToStatusPointers.proxyAsync(Path.key(hashtag).sortedMapRangeFrom(0L, STREAM_QUERY_LIMIT),
                callback);
    }

    public CompletableFuture<ProxyState<SortedMap>> proxyDirectTimeline(long accountId,
            ProxyState.Callback<SortedMap> callback) {
        return accountIdToDirectMessagesById.proxyAsync(Path.key(accountId).sortedMapRangeFrom(0L, STREAM_QUERY_LIMIT),
                callback);
    }

    // TODO: Idk if this works
    public CompletableFuture<List<String>> getFollowedHashtags(long accountId) {
        return hashtagToFollowers.selectAsync(Path.key(accountId).all())
                .thenApply(entries -> entries.stream()
                        .map(Object::toString)
                        .collect(Collectors.toList()));
    }

    // eSports

    public void fetchAllActiveLolPlayers() {
        String filter = String.format("active=1,game.id=2");
        fetchAndStorePlayers(filter, "id-asc", 0, 50);
    }

    public void fetchAllActiveLolTeams() {
        String filter = String.format("active=1,game.id=2");
        fetchAndStoreTeams(filter, "id-asc", 0, 50);
    }

    public void fetchAllLolAssets() {
        String filter = "game.id=2";
        fetchAndStoreAssets(filter, "id-asc", 0, 50);
    }

    public void fetchAllLolTournaments() {
        String filter = "game.id=2";
        fetchAndStoreTournaments(filter, "id-asc", 0, 50);
    }

    public void fetchAllLolSubstages() {
        String filter = "game.id=2";
        fetchAndStoreSubstages(filter, "id-asc", 0, 50);
    }

    public void fetchAllLolCasters() {
        String filter = "game.id=2";
        fetchAndStoreCasters(filter, "id-asc", 0, 50);
    }

    public void fetchAllLolSeries(LocalDate startDate, LocalDate endDate) {
        String startDateString = startDate.atStartOfDay(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT);
        String endDateString = endDate.atTime(23, 59, 59).atZone(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT);
        String filter = String.format("game.id=2,start>=%s,start<=%s", startDateString, endDateString);
        fetchAndStoreSeries(filter, "start-asc", 0, 50);
    }

    private void fetchAndStoreSeries(String filter, String order, int skip, int take) {
        try {
            waitIfNecessary();
            String seriesData = apiClient.getSeries(filter, order, skip, take);
            logger.info("fetchAndStoreSeries - API Response: " + seriesData);
            updateRateLimitInfo(apiClient.getLastResponseHeaders());

            List<PostSeries> postSeriesList = parseJsonToPostSeriesList(seriesData);
            logger.info(
                    "fetchAndStoreSeries - Parsed PostSeries List: " + objectMapper.writeValueAsString(postSeriesList));

            for (PostSeries postSeries : postSeriesList) {
                Series series = convertToThriftSeries(postSeries);
                logger.info("fetchAndStoreSeries - Converted Series: " + objectMapper.writeValueAsString(series));
                seriesDepot.appendAsync(series).join();
                fetchAndStoreRostersForSeries(postSeries.id);
                fetchAndStoreMatchesForSeries(postSeries.id);
                updateTeamSeriesStats(postSeries);
            }

            if (postSeriesList.size() == take) {
                logger.info("fetchAndStoreSeries - Fetching next batch with skip: " + (skip + take));
                fetchAndStoreSeries(filter, order, skip + take, take);
            } else {
                logger.info("fetchAndStoreSeries - All series and their matches have been fetched and stored.");
            }
        } catch (Exception e) {
            handleException(e, "series batch", skip);
        }
    }

    private void fetchAndStoreMatch(int matchId) {
        try {
            waitIfNecessary();
            String matchData = apiClient.getMatch(matchId);
            logger.info("fetchAndStoreMatch - API Response for matchId " + matchId + ": " + matchData);
            updateRateLimitInfo(apiClient.getLastResponseHeaders());

            List<PostMatch> postMatch = parseJsonToPostMatchList(matchData);
            logger.info("fetchAndStoreMatch - Parsed PostMatch: " + objectMapper.writeValueAsString(postMatch));

            Match match = convertToThriftMatch(postMatch.get(0));
            logger.info("fetchAndStoreMatch - Converted Match: " + objectMapper.writeValueAsString(match));

            matchDepot.appendAsync(match).join();
            fetchAndStoreRostersForMatch(match.getId());

            if (isCoverageAvailable(match)) {
                logger.info("fetchAndStoreMatch - Fetching LoL Match Summary for matchId: " + match.getId());
                fetchAndStoreLolMatchSummary(match.getId());
            } else {
                logger.info("fetchAndStoreMatch - Match " + match.getId()
                        + " summary is not available. Skipping summary fetch.");
            }

        } catch (Exception e) {
            handleException(e, "match", matchId);
        }
    }

    private void fetchAndStoreMatchesForSeries(int seriesId) {
        try {
            waitIfNecessary();
            String matchesData = apiClient.getMatchesForSeries(seriesId, null, "start-asc", 0, 50);
            logger.info("fetchAndStoreMatchesForSeries - API Response for seriesId " + seriesId + ": " + matchesData);
            updateRateLimitInfo(apiClient.getLastResponseHeaders());

            List<PostMatch> postMatches = parseJsonToPostMatchList(matchesData);
            logger.info("fetchAndStoreMatchesForSeries - Parsed PostMatch List: "
                    + objectMapper.writeValueAsString(postMatches));

            for (PostMatch postMatch : postMatches) {
                Match match = convertToThriftMatch(postMatch);
                logger.info(
                        "fetchAndStoreMatchesForSeries - Converted Match: " + objectMapper.writeValueAsString(match));
                matchDepot.appendAsync(match).join();
                fetchAndStoreRostersForMatch(match.getId());

                if (isCoverageAvailable(match)) {
                    logger.info(
                            "fetchAndStoreMatchesForSeries - Fetching LoL Match Summary for matchId: " + match.getId());
                    fetchAndStoreLolMatchSummary(match.getId());
                } else {
                    logger.info("fetchAndStoreMatchesForSeries - Match " + match.getId()
                            + " summary is not available. Skipping summary fetch.");
                }
            }
        } catch (Exception e) {
            handleException(e, "matches for series", seriesId);
        }
    }

    private void fetchAndStoreRostersForSeries(int seriesId) {
        try {
            waitIfNecessary();
            String rostersData = apiClient.getSeriesRosters(seriesId);
            logger.info("fetchAndStoreRostersForSeries - API Response for seriesId " + seriesId + ": " + rostersData);
            updateRateLimitInfo(apiClient.getLastResponseHeaders());

            List<PostRoster> postRosters = parseJsonToPostRosterList(rostersData);
            logger.info("fetchAndStoreRostersForSeries - Parsed PostRoster List: "
                    + objectMapper.writeValueAsString(postRosters));

            for (PostRoster postRoster : postRosters) {
                storeRoster(postRoster);
                logger.info("fetchAndStoreRostersForSeries - Stored Roster: "
                        + objectMapper.writeValueAsString(postRoster));
            }
        } catch (Exception e) {
            handleException(e, "rosters for series", seriesId);
        }
    }

    private void fetchAndStoreRostersForMatch(int matchId) {
        try {
            waitIfNecessary();
            String rostersData = apiClient.getMatchRosters(matchId);
            logger.info("fetchAndStoreRostersForMatch - API Response for matchId " + matchId + ": " + rostersData);
            updateRateLimitInfo(apiClient.getLastResponseHeaders());

            List<PostRoster> postRosters = parseJsonToPostRosterList(rostersData);
            logger.info("fetchAndStoreRostersForMatch - Parsed PostRoster List: "
                    + objectMapper.writeValueAsString(postRosters));

            for (PostRoster postRoster : postRosters) {
                storeRoster(postRoster);
                logger.info(
                        "fetchAndStoreRostersForMatch - Stored Roster: " + objectMapper.writeValueAsString(postRoster));
            }
        } catch (Exception e) {
            handleException(e, "rosters for match", matchId);
        }
    }

    private void storeRoster(PostRoster postRoster) {
        logger.info("storeRoster - Storing Roster with ID: " + postRoster.id);
        rosterCache.computeIfAbsent(postRoster.id, id -> {
            Roster newRoster = convertToThriftRoster(postRoster);
            try {
                logger.info("storeRoster - Converted Roster: " + objectMapper.writeValueAsString(newRoster));
            } catch (JsonProcessingException e) {
                logger.error("Json processing exception when converting roster from JSON to Thrift");
            }
            rosterDepot.appendAsync(newRoster).join();
            return newRoster;
        });
    }

    private void fetchAndStoreTeams(String filter, String order, int skip, int take) {
        try {
            waitIfNecessary();
            String teamsData = apiClient.getTeams(filter, order, skip, take);
            logger.info("fetchAndStoreTeams - API Response: " + teamsData);
            updateRateLimitInfo(apiClient.getLastResponseHeaders());

            List<PostTeam> postTeams = parseJsonToPostTeamList(teamsData);
            logger.info("fetchAndStoreTeams - Parsed PostTeam List: " + objectMapper.writeValueAsString(postTeams));

            for (PostTeam postTeam : postTeams) {
                Team team = convertToThriftTeam(postTeam);
                logger.info("fetchAndStoreTeams - Converted Team: " + objectMapper.writeValueAsString(team));
                teamDepot.appendAsync(team).join();

                // Fetch and store series IDs for the team
                fetchAndStoreTeamSeries(team.getId());
            }

            if (postTeams.size() == take) {
                logger.info("fetchAndStoreTeams - Fetching next batch with skip: " + (skip + take));
                fetchAndStoreTeams(filter, order, skip + take, take);
            } else {
                logger.info("fetchAndStoreTeams - All active teams have been fetched and stored.");
            }
        } catch (Exception e) {
            handleException(e, "teams batch", skip);
        }
    }

    private void fetchAndStoreTeamSeries(int teamId) {
        fetchAndStoreTeamSeries(teamId, null, null, 0, 50, new ArrayList<>());
    }

    private void fetchAndStoreTeamSeries(int teamId, String filter, String order, int skip, int take,
            List<Integer> accumulatedSeriesIds) {
        try {
            waitIfNecessary();
            String seriesData = apiClient.getTeamSeries(teamId, filter, order, skip, take);
            logger.info("fetchAndStoreTeamSeries - API Response for team {}: {}", teamId, seriesData);
            updateRateLimitInfo(apiClient.getLastResponseHeaders());

            List<PostSeries> postSeriesList = parseJsonToPostSeriesList(seriesData);
            logger.info("fetchAndStoreTeamSeries - Parsed PostSeries List for team {}: {}", teamId,
                    objectMapper.writeValueAsString(postSeriesList));

            // Extract series IDs from PostSeries objects
            List<Integer> seriesIds = postSeriesList.stream()
                    .map(postSeries -> postSeries.id)
                    .collect(Collectors.toList());

            accumulatedSeriesIds.addAll(seriesIds);

            if (postSeriesList.size() == take) {
                logger.info("fetchAndStoreTeamSeries - Fetching next batch with skip: {}", (skip + take));
                fetchAndStoreTeamSeries(teamId, filter, order, skip + take, take, accumulatedSeriesIds);
            } else {
                logger.info("fetchAndStoreTeamSeries - All series for team {} have been fetched.", teamId);

                // Create a Schedule object and append it to the scheduleDepot
                Schedule schedule = new Schedule();
                schedule.setId(teamId);
                schedule.setSeriesIds(accumulatedSeriesIds);

                scheduleDepot.appendAsync(schedule).join();
            }
        } catch (Exception e) {
            handleException(e, "team series batch", skip);
        }
    }

    private void fetchAndStorePlayers(String filter, String order, int skip, int take) {
        try {
            waitIfNecessary();
            String playersData = apiClient.getPlayers(filter, order, skip, take);
            logger.info("fetchAndStorePlayers - API Response: " + playersData);
            updateRateLimitInfo(apiClient.getLastResponseHeaders());

            List<PostPlayer> postPlayers = parseJsonToPostPlayerList(playersData);
            logger.info(
                    "fetchAndStorePlayers - Parsed PostPlayer List: " + objectMapper.writeValueAsString(postPlayers));

            for (PostPlayer postPlayer : postPlayers) {
                Player player = convertToThriftPlayer(postPlayer);
                logger.info("fetchAndStorePlayers - Converted Player: " + objectMapper.writeValueAsString(player));
                playerDepot.appendAsync(player).join();
            }

            if (postPlayers.size() == take) {
                logger.info("fetchAndStorePlayers - Fetching next batch with skip: " + (skip + take));
                fetchAndStorePlayers(filter, order, skip + take, take);
            } else {

                logger.info("fetchAndStorePlayers - All active players have been fetched and stored.");
            }
        } catch (Exception e) {
            handleException(e, "players batch", skip);
        }
    }

    public void fetchAndStoreLolMatchSummary(int matchId) {
        try {
            waitIfNecessary();
            String summaryData = apiClient.getMatchSummary(matchId);
            logger.info("fetchAndStoreLolMatchSummary - API Response for matchId " + matchId + ": " + summaryData);
            updateRateLimitInfo(apiClient.getLastResponseHeaders());

            PostLolMatchSummary postLolSummary = parseJsonToPostLolMatchSummary(summaryData);
            if (postLolSummary != null) {
                logger.info("fetchAndStoreLolMatchSummary - Parsed PostLolMatchSummary: "
                        + objectMapper.writeValueAsString(postLolSummary));
                LolMatchSummary lolSummary = convertToThriftLolMatchSummary(postLolSummary);
                logger.info("fetchAndStoreLolMatchSummary - Converted LolMatchSummary: "
                        + objectMapper.writeValueAsString(lolSummary));
                lolMatchSummaryDepot.appendAsync(lolSummary).join();

                // Process and store player stats
                List<LolPlayerSummary> allPlayers = new ArrayList<>();
                allPlayers.addAll(lolSummary.teams.getHome().getPlayers());
                allPlayers.addAll(lolSummary.teams.getAway().getPlayers());

                for (LolPlayerSummary playerSummary : allPlayers) {
                    lolPlayerSeasonStatsDepot.appendAsync(playerSummary).join();
                    logger.info("fetchAndStoreLolMatchSummary - Stored Player Summary: "
                            + objectMapper.writeValueAsString(playerSummary));
                }

                // Process and store team stats
                CompletableFuture<Void> homeTeamFuture = storeTeamSummary(lolSummary.teams.getHome());
                CompletableFuture<Void> awayTeamFuture = storeTeamSummary(lolSummary.teams.getAway());

                // Update aggregated team stats
                homeTeamFuture.thenRun(() -> updateTeamAggStats(lolSummary.getTeams().getHome()));
                awayTeamFuture.thenRun(() -> updateTeamAggStats(lolSummary.getTeams().getAway()));

                // Update aggregated player stats
                homeTeamFuture.thenRun(() -> updatePlayerAggStats(lolSummary.getTeams().getHome()));
                awayTeamFuture.thenRun(() -> updatePlayerAggStats(lolSummary.getTeams().getAway()));

                CompletableFuture.allOf(homeTeamFuture, awayTeamFuture).join();
                logger.info("fetchAndStoreLolMatchSummary - Stored Team and Player Summaries for matchId " + matchId);
            } else {
                logger.warn("fetchAndStoreLolMatchSummary - Failed to parse match summary for match " + matchId);
            }
        } catch (Exception e) {
            handleException(e, "LoL match summary", matchId);
        }
    }

    private CompletableFuture<Void> storeTeamSummary(LolTeamSummary teamSummary) {
        logger.info("storeTeamSummary - Starting processing for team roster ID: " + teamSummary.getRoster().getId());

        // Fetch teamId from rosterId
        CompletableFuture<Integer> teamIdFuture = getTeamIdFromRosterId(teamSummary.getRoster().getId())
                .exceptionally(ex -> {
                    logger.error("Error fetching teamId from rosterId: " + teamSummary.getRoster().getId(), ex);
                    return null;
                });

        // Fetch Match using matchId from teamSummary
        CompletableFuture<Match> matchFuture = getMatchFromMatchId.invokeAsync(teamSummary.getMatchId())
                .exceptionally(ex -> {
                    logger.error("Error fetching Match for matchId: " + teamSummary.getMatchId(), ex);
                    return null;
                });

        // Once we have the Match, fetch the Series
        CompletableFuture<Series> seriesFuture = matchFuture.thenCompose(match -> {
            if (match != null) {
                return getSeriesFromSeriesId.invokeAsync(match.getSeriesId())
                        .exceptionally(ex -> {
                            logger.error("Error fetching Series for seriesId: " + match.getSeriesId(), ex);
                            return null;
                        });
            } else {
                logger.warn("storeTeamSummary - Match not found for matchId: " + teamSummary.getMatchId());
                return CompletableFuture.completedFuture(null);
            }
        });

        // Fetch opponent team information
        CompletableFuture<Void> opponentFuture = matchFuture.thenCompose(match -> {
            if (match != null) {
                // Get participants
                List<Participant> participants = match.getParticipants();

                // Identify opponent rosterId
                int teamRosterId = teamSummary.getRoster().getId();
                Integer opponentRosterId = null;
                for (Participant participant : participants) {
                    if (participant.getRoster().getId() != teamRosterId) {
                        opponentRosterId = participant.getRoster().getId();
                        break;
                    }
                }

                if (opponentRosterId != null) {
                    logger.info("storeTeamSummary - Found opponent rosterId: " + opponentRosterId);

                    // Fetch opponent teamId
                    return getTeamIdFromRosterId(opponentRosterId).thenCompose(opponentTeamId -> {
                        if (opponentTeamId != null) {
                            logger.info("storeTeamSummary - Found opponent teamId: " + opponentTeamId);

                            // Fetch opponent Team object
                            return getTeamFromTeamId.invokeAsync(opponentTeamId).thenAccept(opponentTeam -> {
                                if (opponentTeam != null) {
                                    teamSummary.setOpponent(opponentTeam);
                                    logger.info("storeTeamSummary - Opponent Team set for team ID: "
                                            + teamSummary.getTeamId() + " with opponent team ID: "
                                            + opponentTeam.getId());
                                } else {
                                    logger.warn("storeTeamSummary - Opponent Team not found for team ID: "
                                            + opponentTeamId);
                                }
                            }).exceptionally(ex -> {
                                logger.error("Error fetching opponent Team for teamId: " + opponentTeamId, ex);
                                return null;
                            });
                        } else {
                            logger.warn("storeTeamSummary - Opponent teamId not found");
                            return CompletableFuture.completedFuture(null);
                        }
                    }).exceptionally(ex -> {
                        logger.error("Error fetching opponent teamId", ex);
                        return null;
                    });
                } else {
                    logger.warn("storeTeamSummary - Opponent rosterId not found in match participants.");
                    return CompletableFuture.completedFuture(null);
                }
            } else {
                logger.warn("storeTeamSummary - Match is null for matchId: " + teamSummary.getMatchId());
                return CompletableFuture.completedFuture(null);
            }
        });

        // Combine all futures
        return CompletableFuture.allOf(teamIdFuture, seriesFuture, opponentFuture)
                .thenCompose(v -> {
                    Integer teamId = teamIdFuture.join();
                    Series series = seriesFuture.join();

                    // Handle exceptions in opponentFuture
                    try {
                        opponentFuture.join();
                    } catch (Exception e) {
                        logger.error("storeTeamSummary - Exception in opponentFuture: ", e);
                    }

                    if (teamId != null) {
                        teamSummary.setTeamId(teamId);
                        logger.info("storeTeamSummary - Team ID set to: " + teamId);
                    } else {
                        logger.warn("storeTeamSummary - Failed to fetch team ID for rosterId: "
                                + teamSummary.getRoster().getId());
                    }

                    if (series != null) {
                        teamSummary.setStart(series.getStart());
                        logger.info("storeTeamSummary - Start time set for team ID: " + teamId + " with start: "
                                + teamSummary.getStart());
                    } else {
                        logger.warn("storeTeamSummary - Series not found for matchId: " + teamSummary.getMatchId());
                    }

                    // Log the opponent information
                    if (teamSummary.isSetOpponent()) {
                        logger.info("storeTeamSummary - Opponent is set: " + teamSummary.getOpponent().getName()
                                + " (ID: " + teamSummary.getOpponent().getId() + ")");
                    } else {
                        logger.warn("storeTeamSummary - Opponent is NOT set for team ID: " + teamId);
                    }

                    // Now store the teamSummary
                    return lolTeamSeasonStatsDepot.appendAsync(teamSummary)
                            .thenAccept(result -> {
                                logger.info(
                                        "storeTeamSummary - Successfully stored team summary for team ID: " + teamId);
                            }).exceptionally(ex -> {
                                logger.error("Error storing teamSummary for team ID: " + teamId, ex);
                                return null;
                            });
                });
    }

    private void updateTeamSeriesStats(PostSeries postSeries) {
        if (!"over".equals(postSeries.lifecycle)) {
            logger.info("updateTeamSeriesStats - Skipping series id " + postSeries.id + " with lifecycle: "
                    + postSeries.lifecycle);
            return; // Skip this series
        }

        for (PostParticipant participant : postSeries.participants) {
            if (participant.roster == null) {
                logger.warn("Participant roster is null for series id " + postSeries.id);
                continue; // Skip this participant
            }
            int rosterId = participant.roster.id;
            boolean isWinner = participant.winner;

            CompletableFuture.runAsync(() -> {
                getTeamIdFromRosterId(rosterId)
                        .thenCompose(teamId -> {
                            if (teamId == null) {
                                logger.warn("No team ID found for rosterId: " + rosterId);
                                return CompletableFuture.completedFuture(null);
                            }

                            return getLolTeamAggStatsFromTeamId.invokeAsync(teamId)
                                    .thenCompose(existingStats -> {
                                        // Initialize if null
                                        if (existingStats == null) {
                                            existingStats = initializeNewTeamAggStats(teamId);
                                        }

                                        // Update series stats
                                        existingStats.setTotalSeries(existingStats.getTotalSeries() + 1);
                                        if (isWinner) {
                                            existingStats.setTotalSeriesWins(existingStats.getTotalSeriesWins() + 1);
                                        } else {
                                            existingStats
                                                    .setTotalSeriesLosses(existingStats.getTotalSeriesLosses() + 1);
                                        }

                                        // Store updated aggregated stats
                                        return lolTeamAggStatsDepot.appendAsync(existingStats)
                                                .thenRun(() -> logger.info(
                                                        "updateTeamSeriesStats - Successfully updated series stats for teamId: "
                                                                + teamId));
                                    });
                        })
                        .exceptionally(e -> {
                            logger.error("updateTeamSeriesStats - Error updating series stats for rosterId " + rosterId,
                                    e);
                            return null;
                        });
            });
        }
    }

    private void updateTeamAggStats(LolTeamSummary teamSummary) {
        CompletableFuture.runAsync(() -> {
            int teamId = teamSummary.getTeamId();
            logger.info("updateTeamAggregatedStats - Updating aggregated stats for teamId: " + teamId);

            getLolTeamAggStatsFromTeamId.invokeAsync(teamId)
                    .thenCompose(existingStats -> {
                        // Initialize if null
                        if (existingStats == null) {
                            existingStats = initializeNewTeamAggStats(teamId);
                        }

                        // Update basic aggregated stats
                        updateBasicStats(existingStats, teamSummary);

                        // Update neutral objectives
                        updateNeutralObjectiveStats(existingStats, teamSummary);

                        // Calculate averages
                        calculateAverages(existingStats);

                        // Store updated aggregated stats
                        return lolTeamAggStatsDepot.appendAsync(existingStats)
                                .thenRun(() -> logger.info(
                                        "updateTeamAggregatedStats - Successfully updated aggregated stats for teamId: "
                                                + teamId));
                    })
                    .exceptionally(e -> {
                        logger.error("updateTeamAggStats - Error updating aggregated stats for teamId "
                                + teamSummary.getTeamId(), e);
                        return null;
                    });
        });
    }

    // Helper method to initialize a new LolTeamAggStats object
    private LolTeamAggStats initializeNewTeamAggStats(int teamId) {
        LolTeamAggStats stats = new LolTeamAggStats();
        stats.setId(teamId);
        // All integer fields in Thrift structs are initialized to 0 by default
        return stats;
    }

    // Helper method to update basic stats
    private void updateBasicStats(LolTeamAggStats existingStats, LolTeamSummary teamSummary) {
        existingStats.setTotalMatches(existingStats.getTotalMatches() + 1);
        if (teamSummary.isIsWinner()) {
            existingStats.setTotalWins(existingStats.getTotalWins() + 1);
        } else {
            existingStats.setTotalLosses(existingStats.getTotalLosses() + 1);
        }
        existingStats.setTotalScore(existingStats.getTotalScore() + teamSummary.getScore());
        existingStats.setTotalGoldEarned(existingStats.getTotalGoldEarned() + teamSummary.getGoldEarned());
        existingStats.setTotalTurretsDestroyed(
                existingStats.getTotalTurretsDestroyed() + teamSummary.getTurretsDestroyed());
        existingStats.setTotalInhibitorsDestroyed(
                existingStats.getTotalInhibitorsDestroyed() + teamSummary.getInhibitorsDestroyed());

        // Update currentWinStreak
        if (teamSummary.isIsWinner()) {
            existingStats.setCurrentWinStreak(
                    existingStats.getCurrentWinStreak() >= 0
                            ? existingStats.getCurrentWinStreak() + 1
                            : 1); // Reset negative streak
        } else {
            existingStats.setCurrentWinStreak(
                    existingStats.getCurrentWinStreak() <= 0
                            ? existingStats.getCurrentWinStreak() - 1
                            : -1); // Reset positive streak
        }
    }

    // Helper method to update neutral objective stats
    private void updateNeutralObjectiveStats(LolTeamAggStats existingStats, LolTeamSummary teamSummary) {
        int dragonsKilled = 0;
        int baronsKilled = 0;
        int heraldsKilled = 0;
        int voidGrubsKilled = 0;

        // Safely extract neutral creeps data
        List<LolEliteCreepKills> eliteCreepKills = Optional.ofNullable(teamSummary.getCreeps())
                .map(LolCreeps::getNeutrals)
                .map(LolNeutralCreeps::getKills)
                .map(LolNeutralCreepKills::getPerEliteType)
                .orElse(Collections.emptyList());

        // Iterate over elite creep kills
        for (LolEliteCreepKills eliteKill : eliteCreepKills) {
            int eliteId = eliteKill.getElite().getId();
            int kills = eliteKill.getTotal();
            ObjectiveType objectiveType = getObjectiveTypeByEliteId(eliteId);

            switch (objectiveType) {
                case DRAGON:
                    dragonsKilled += kills;
                    break;
                case BARON:
                    baronsKilled += kills;
                    break;
                case HERALD:
                    heraldsKilled += kills;
                    break;
                case VOID_GRUB:
                    voidGrubsKilled += kills;
                    break;
                default:
                    logger.warn("Unknown elite ID encountered: " + eliteId);
                    break;
            }
        }

        // Update total kills for neutral objectives
        existingStats.setTotalDragonKills(existingStats.getTotalDragonKills() + dragonsKilled);
        existingStats.setTotalBaronKills(existingStats.getTotalBaronKills() + baronsKilled);
        existingStats.setTotalHeraldKills(existingStats.getTotalHeraldKills() + heraldsKilled);
        existingStats.setTotalVoidGrubKills(existingStats.getTotalVoidGrubKills() + voidGrubsKilled);
    }

    // Helper method to calculate averages
    private void calculateAverages(LolTeamAggStats existingStats) {
        int totalMatches = existingStats.getTotalMatches();

        existingStats.setAverageScore((double) existingStats.getTotalScore() / totalMatches);
        existingStats.setAverageGoldEarned((double) existingStats.getTotalGoldEarned() / totalMatches);
        existingStats.setAverageTurretsDestroyed((double) existingStats.getTotalTurretsDestroyed() / totalMatches);
        existingStats
                .setAverageInhibitorsDestroyed((double) existingStats.getTotalInhibitorsDestroyed() / totalMatches);
        existingStats.setAverageDragonKills((double) existingStats.getTotalDragonKills() / totalMatches);
        existingStats.setAverageBaronKills((double) existingStats.getTotalBaronKills() / totalMatches);
        existingStats.setAverageHeraldKills((double) existingStats.getTotalHeraldKills() / totalMatches);
        existingStats.setAverageVoidGrubKills((double) existingStats.getTotalVoidGrubKills() / totalMatches);
    }

    // Helper enum and method for mapping elite IDs to objective types
    public enum ObjectiveType {
        DRAGON,
        BARON,
        HERALD,
        VOID_GRUB,
        UNKNOWN
    }

    private ObjectiveType getObjectiveTypeByEliteId(int eliteId) {
        switch (eliteId) {
            case 5426:
            case 5427:
            case 5428:
            case 5429:
            case 5430:
            case 7098:
            case 7097:
                return ObjectiveType.DRAGON;
            case 5431:
                return ObjectiveType.BARON;
            case 5432:
                return ObjectiveType.HERALD;
            case 8602:
                return ObjectiveType.VOID_GRUB;
            default:
                return ObjectiveType.UNKNOWN;
        }
    }

    private void updatePlayerAggStats(LolTeamSummary teamSummary) {
        CompletableFuture.runAsync(() -> {
            List<LolPlayerSummary> players = teamSummary.getPlayers();
            for (LolPlayerSummary playerSummary : players) {
                int playerId = playerSummary.getId();
                logger.info("updatePlayerAggStats - Updating aggregated stats for playerId: " + playerId);

                getLolPlayerAggStatsFromPlayerId.invokeAsync(playerId)
                        .thenCompose(existingStats -> {
                            // Initialize if null
                            if (existingStats == null) {
                                existingStats = initializeNewPlayerAggStats(playerId);
                            }

                            // Update basic aggregated stats
                            updatePlayerBasicStats(existingStats, playerSummary);

                            // Calculate averages
                            calculatePlayerAverages(existingStats);

                            // Store updated aggregated stats
                            return lolPlayerAggStatsDepot.appendAsync(existingStats)
                                    .thenRun(() -> logger.info(
                                            "updatePlayerAggStats - Successfully updated aggregated stats for playerId: "
                                                    + playerId));
                        })
                        .exceptionally(e -> {
                            logger.error(
                                    "updatePlayerAggStats - Error updating aggregated stats for playerId: " + playerId,
                                    e);
                            return null;
                        });
            }
        });
    }

    // Helper method to initialize a new LolPlayerAggStats object
    private LolPlayerAggStats initializeNewPlayerAggStats(int playerId) {
        LolPlayerAggStats stats = new LolPlayerAggStats();
        stats.setId(playerId);
        stats.setTotalMatches(0);
        stats.setTotalKills(0);
        stats.setTotalDeaths(0);
        stats.setTotalAssists(0);
        stats.setAverageKills(0.0);
        stats.setAverageDeaths(0.0);
        stats.setAverageAssists(0.0);
        stats.setTotalCreepScore(0);
        stats.setAverageCreepScore(0.0);
        return stats;
    }

    // Helper method to update basic player stats
    private void updatePlayerBasicStats(LolPlayerAggStats existingStats, LolPlayerSummary playerSummary) {
        existingStats.setTotalMatches(existingStats.getTotalMatches() + 1);
        existingStats.setTotalKills(existingStats.getTotalKills() + playerSummary.getKills().getTotal());
        existingStats.setTotalDeaths(existingStats.getTotalDeaths() + playerSummary.getDeaths().getTotal());
        existingStats.setTotalAssists(existingStats.getTotalAssists() + playerSummary.getAssists().getTotal());
        existingStats.setTotalCreepScore(existingStats.getTotalCreepScore() +
                playerSummary.getCreeps().getOverall().getKills().getTotal());
    }

    // Helper method to calculate averages for player stats
    private void calculatePlayerAverages(LolPlayerAggStats existingStats) {
        int totalMatches = existingStats.getTotalMatches();
        if (totalMatches == 0) {
            existingStats.setAverageKills(0.0);
            existingStats.setAverageDeaths(0.0);
            existingStats.setAverageAssists(0.0);
            existingStats.setAverageCreepScore(0.0);
            return;
        }

        existingStats.setAverageKills((double) existingStats.getTotalKills() / totalMatches);
        existingStats.setAverageDeaths((double) existingStats.getTotalDeaths() / totalMatches);
        existingStats.setAverageAssists((double) existingStats.getTotalAssists() / totalMatches);
        existingStats.setAverageCreepScore((double) existingStats.getTotalCreepScore() / totalMatches);
    }

    private CompletableFuture<Integer> getTeamIdFromRosterId(int rosterId) {
        logger.info("getTeamIdFromRosterId - Fetching team ID from rosterId: " + rosterId);
        return getRosterFromRosterId.invokeAsync(rosterId)
                .thenApply(roster -> {
                    if (roster != null) {
                        logger.info("getTeamIdFromRosterId - Found team ID: " + roster.getTeamId() + " for rosterId: "
                                + rosterId);
                        return roster.getTeamId();
                    } else {
                        logger.warn("getTeamIdFromRosterId - No roster found for rosterId: " + rosterId);
                        return null;
                    }
                });
    }

    private void fetchAndStoreAssets(String filter, String order, int skip, int take) {
        try {
            waitIfNecessary();
            String assetsData = apiClient.getAssets(filter, order, skip, take);
            logger.info("fetchAndStoreAssets - API Response: " + assetsData);
            updateRateLimitInfo(apiClient.getLastResponseHeaders());

            List<PostAsset> postAssets = parseJsonToPostAssetList(assetsData);
            logger.info("fetchAndStoreAssets - Parsed PostAsset List: " + objectMapper.writeValueAsString(postAssets));

            for (PostAsset postAsset : postAssets) {
                Asset asset = convertToThriftAsset(postAsset);
                logger.info("fetchAndStoreAssets - Converted Asset: " + objectMapper.writeValueAsString(asset));
                assetDepot.appendAsync(asset).join();
            }

            if (postAssets.size() == take) {
                logger.info("fetchAndStoreAssets - Fetching next batch with skip: " + (skip + take));
                fetchAndStoreAssets(filter, order, skip + take, take);
            } else {
                logger.info("fetchAndStoreAssets - All assets have been fetched and stored.");
            }
        } catch (Exception e) {
            handleException(e, "assets batch", skip);
        }
    }

    private void fetchAndStoreTournaments(String filter, String order, int skip, int take) {
        try {
            waitIfNecessary();
            String tournamentsData = apiClient.getTournaments(filter, order, skip, take);
            logger.info("fetchAndStoreTournaments - API Response: " + tournamentsData);
            updateRateLimitInfo(apiClient.getLastResponseHeaders());

            List<PostTournament> postTournaments = parseJsonToPostTournamentList(tournamentsData);
            logger.info("fetchAndStoreTournaments - Parsed PostTournament List: "
                    + objectMapper.writeValueAsString(postTournaments));

            for (PostTournament postTournament : postTournaments) {
                Tournament tournament = convertToThriftTournament(postTournament);
                logger.info("fetchAndStoreTournaments - Converted Tournament: "
                        + objectMapper.writeValueAsString(tournament));
                tournamentDepot.appendAsync(tournament).join();
            }

            if (postTournaments.size() == take) {
                logger.info("fetchAndStoreTournaments - Fetching next batch with skip: " + (skip + take));
                fetchAndStoreTournaments(filter, order, skip + take, take);
            } else {
                logger.info("fetchAndStoreTournaments - All tournaments have been fetched and stored.");
            }
        } catch (Exception e) {
            handleException(e, "tournaments batch", skip);
        }
    }

    private void fetchAndStoreSubstages(String filter, String order, int skip, int take) {
        try {
            waitIfNecessary();
            String substagesData = apiClient.getSubstages(filter, order, skip, take);
            logger.info("fetchAndStoreSubstages - API Response: " + substagesData);
            updateRateLimitInfo(apiClient.getLastResponseHeaders());

            List<PostSubstage> postSubstages = parseJsonToPostSubstageList(substagesData);
            logger.info("fetchAndStoreSubstages - Parsed PostSubstage List: "
                    + objectMapper.writeValueAsString(postSubstages));

            for (PostSubstage postSubstage : postSubstages) {
                Substage substage = convertToThriftSubstage(postSubstage);
                logger.info(
                        "fetchAndStoreSubstages - Converted Substage: " + objectMapper.writeValueAsString(substage));
                substageDepot.appendAsync(substage).join();
            }

            if (postSubstages.size() == take) {
                logger.info("fetchAndStoreSubstages - Fetching next batch with skip: " + (skip + take));
                fetchAndStoreSubstages(filter, order, skip + take, take);
            } else {
                logger.info("fetchAndStoreSubstages - All substages have been fetched and stored.");
            }
        } catch (Exception e) {
            handleException(e, "substages batch", skip);
        }
    }

    private void fetchAndStoreCasters(String filter, String order, int skip, int take) {
        try {
            waitIfNecessary();
            String castersData = apiClient.getCasters(filter, order, skip, take);
            logger.info("fetchAndStoreCasters - API Response: " + castersData);
            updateRateLimitInfo(apiClient.getLastResponseHeaders());

            List<PostCaster> postCasters = parseJsonToPostCasterList(castersData);
            logger.info(
                    "fetchAndStoreCasters - Parsed PostCaster List: " + objectMapper.writeValueAsString(postCasters));

            for (PostCaster postCaster : postCasters) {
                Caster caster = convertToThriftCaster(postCaster);
                logger.info("fetchAndStoreCasters - Converted Caster: " + objectMapper.writeValueAsString(caster));
                casterDepot.appendAsync(caster).join();
            }

            if (postCasters.size() == take) {
                logger.info("fetchAndStoreCasters - Fetching next batch with skip: " + (skip + take));
                fetchAndStoreCasters(filter, order, skip + take, take);
            } else {
                logger.info("fetchAndStoreCasters - All casters have been fetched and stored.");
            }
        } catch (Exception e) {
            handleException(e, "casters batch", skip);
        }
    }

    private List<PostSeries> parseJsonToPostSeriesList(String jsonData) throws Exception {
        logger.info("parseJsonToPostSeriesList - Parsing JSON data.");
        JsonNode rootNode = objectMapper.readTree(jsonData);

        if (rootNode.has("error_type")) {
            String errorType = rootNode.get("error_type").asText();
            String errorMessage = rootNode.has("message") ? rootNode.get("message").asText() : "Unknown error";
            logger.error("parseJsonToPostSeriesList - API Error: " + errorType + " - " + errorMessage);
            throw new ApiException("API Error: " + errorType + " - " + errorMessage);
        }

        List<PostSeries> seriesList = new ArrayList<>();

        if (rootNode.isArray()) {
            for (JsonNode node : rootNode) {
                seriesList.add(objectMapper.treeToValue(node, PostSeries.class));
            }
        } else if (rootNode.isObject()) {
            if (rootNode.has("series") && rootNode.get("series").isArray()) {
                for (JsonNode seriesNode : rootNode.get("series")) {
                    seriesList.add(objectMapper.treeToValue(seriesNode, PostSeries.class));
                }
            } else {
                seriesList.add(objectMapper.treeToValue(rootNode, PostSeries.class));
            }
        } else {
            logger.error("parseJsonToPostSeriesList - Unexpected Series JSON structure: " + jsonData);
            throw new IllegalArgumentException("Unexpected JSON structure for series");
        }

        logger.info("parseJsonToPostSeriesList - Successfully parsed " + seriesList.size() + " PostSeries objects.");
        return seriesList;
    }

    private List<PostMatch> parseJsonToPostMatchList(String jsonData) throws Exception {
        logger.info("parseJsonToPostMatchList - Parsing JSON data.");
        JsonNode rootNode = objectMapper.readTree(jsonData);

        if (rootNode.has("error_type")) {
            String errorType = rootNode.get("error_type").asText();
            String errorMessage = rootNode.has("message") ? rootNode.get("message").asText() : "Unknown error";
            logger.error("parseJsonToPostMatchList - API Error: " + errorType + " - " + errorMessage);
            throw new ApiException("API Error: " + errorType + " - " + errorMessage);
        }

        List<PostMatch> matches = new ArrayList<>();

        if (rootNode.isArray()) {
            for (JsonNode node : rootNode) {
                matches.add(objectMapper.treeToValue(node, PostMatch.class));
            }
        } else if (rootNode.isObject()) {
            matches.add(objectMapper.treeToValue(rootNode, PostMatch.class));
        } else {
            logger.warn("parseJsonToPostMatchList - Unexpected Match JSON structure: " + jsonData);
            return Collections.emptyList();
        }

        logger.info("parseJsonToPostMatchList - Successfully parsed " + matches.size() + " PostMatch objects.");
        return matches;
    }

    private List<PostRoster> parseJsonToPostRosterList(String jsonData) throws Exception {
        logger.info("parseJsonToPostRosterList - Parsing JSON data.");
        JsonNode rootNode = objectMapper.readTree(jsonData);

        if (rootNode.has("error_type")) {
            String errorType = rootNode.get("error_type").asText();
            String errorMessage = rootNode.has("message") ? rootNode.get("message").asText() : "Unknown error";
            logger.error("parseJsonToPostRosterList - API Error: " + errorType + " - " + errorMessage);
            throw new ApiException("API Error: " + errorType + " - " + errorMessage);
        }

        List<PostRoster> rosterList = new ArrayList<>();

        if (rootNode.isArray()) {
            for (JsonNode node : rootNode) {
                rosterList.add(objectMapper.treeToValue(node, PostRoster.class));
            }
        } else if (rootNode.isObject()) {
            rosterList.add(objectMapper.treeToValue(rootNode, PostRoster.class));
        } else {
            logger.error("parseJsonToPostRosterList - Unexpected Roster JSON structure: " + jsonData);
            throw new IllegalArgumentException("Unexpected JSON structure for rosters");
        }

        logger.info("parseJsonToPostRosterList - Successfully parsed " + rosterList.size() + " PostRoster objects.");
        return rosterList;
    }

    private List<PostPlayer> parseJsonToPostPlayerList(String jsonData) throws Exception {
        logger.info("parseJsonToPostPlayerList - Parsing JSON data.");
        JsonNode rootNode = objectMapper.readTree(jsonData);

        if (rootNode.has("error_type")) {
            String errorType = rootNode.get("error_type").asText();
            String errorMessage = rootNode.has("message") ? rootNode.get("message").asText() : "Unknown error";
            logger.error("parseJsonToPostPlayerList - API Error: " + errorType + " - " + errorMessage);
            throw new ApiException("API Error: " + errorType + " - " + errorMessage);
        }

        List<PostPlayer> playerList = new ArrayList<>();

        if (rootNode.isArray()) {
            for (JsonNode node : rootNode) {
                playerList.add(objectMapper.treeToValue(node, PostPlayer.class));
            }
        } else if (rootNode.isObject()) {
            playerList.add(objectMapper.treeToValue(rootNode, PostPlayer.class));
        } else {
            logger.error("parseJsonToPostPlayerList - Unexpected Players JSON structure: " + jsonData);
            throw new IllegalArgumentException("Unexpected JSON structure for players");
        }

        logger.info("parseJsonToPostPlayerList - Successfully parsed " + playerList.size() + " PostPlayer objects.");
        return playerList;
    }

    private List<PostTeam> parseJsonToPostTeamList(String jsonData) throws Exception {
        logger.info("parseJsonToPostTeamList - Parsing JSON data.");
        JsonNode rootNode = objectMapper.readTree(jsonData);

        if (rootNode.has("error_type")) {
            String errorType = rootNode.get("error_type").asText();
            String errorMessage = rootNode.has("message") ? rootNode.get("message").asText() : "Unknown error";
            logger.error("parseJsonToPostTeamList - API Error: " + errorType + " - " + errorMessage);
            throw new ApiException("API Error: " + errorType + " - " + errorMessage);
        }

        List<PostTeam> teamList = new ArrayList<>();

        if (rootNode.isArray()) {
            for (JsonNode node : rootNode) {
                teamList.add(objectMapper.treeToValue(node, PostTeam.class));
            }
        } else if (rootNode.isObject()) {
            teamList.add(objectMapper.treeToValue(rootNode, PostTeam.class));
        } else {
            logger.error("parseJsonToPostTeamList - Unexpected Teams JSON structure: " + jsonData);
            throw new IllegalArgumentException("Unexpected JSON structure for teams");
        }

        logger.info("parseJsonToPostTeamList - Successfully parsed " + teamList.size() + " PostTeam objects.");
        return teamList;
    }

    private PostLolMatchSummary parseJsonToPostLolMatchSummary(String jsonData) {
        logger.info("parseJsonToPostLolMatchSummary - Parsing JSON data.");
        try {
            JsonNode rootNode = objectMapper.readTree(jsonData);

            if (rootNode.has("error_type")) {
                String errorType = rootNode.get("error_type").asText();
                String errorMessage = rootNode.has("message") ? rootNode.get("message").asText() : "Unknown error";
                logger.error("parseJsonToPostLolMatchSummary - API Error: " + errorType + " - " + errorMessage);
                throw new ApiException("API Error: " + errorType + " - " + errorMessage);
            }

            if (!rootNode.has("teams") || !rootNode.has("pits") || !rootNode.has("match")) {
                logger.warn(
                        "parseJsonToPostLolMatchSummary - Unexpected LoL match summary JSON structure: " + jsonData);
                return null;
            }

            PostLolMatchSummary summary = objectMapper.treeToValue(rootNode, PostLolMatchSummary.class);
            logger.info("parseJsonToPostLolMatchSummary - Successfully parsed PostLolMatchSummary: "
                    + objectMapper.writeValueAsString(summary));
            return summary;
        } catch (Exception e) {
            logger.error("parseJsonToPostLolMatchSummary - Error parsing LoL match summary: " + e.getMessage());
            return null;
        }
    }

    private List<PostAsset> parseJsonToPostAssetList(String jsonData) throws Exception {
        logger.info("parseJsonToPostAssetList - Parsing JSON data.");
        JsonNode rootNode = objectMapper.readTree(jsonData);

        if (rootNode.has("error_type")) {
            String errorType = rootNode.get("error_type").asText();
            String errorMessage = rootNode.has("message") ? rootNode.get("message").asText() : "Unknown error";
            logger.error("parseJsonToPostAssetList - API Error: " + errorType + " - " + errorMessage);
            throw new ApiException("API Error: " + errorType + " - " + errorMessage);
        }

        List<PostAsset> assetList = new ArrayList<>();

        if (rootNode.isArray()) {
            for (JsonNode node : rootNode) {
                assetList.add(objectMapper.treeToValue(node, PostAsset.class));
            }
        } else if (rootNode.isObject()) {
            assetList.add(objectMapper.treeToValue(rootNode, PostAsset.class));
        } else {
            logger.error("parseJsonToPostAssetList - Unexpected Assets JSON structure: " + jsonData);
            throw new IllegalArgumentException("Unexpected JSON structure for assets");
        }

        logger.info("parseJsonToPostAssetList - Successfully parsed " + assetList.size() + " PostAsset objects.");
        return assetList;
    }

    private List<PostTournament> parseJsonToPostTournamentList(String jsonData) throws Exception {
        logger.info("parseJsonToPostTournamentList - Parsing JSON data.");
        JsonNode rootNode = objectMapper.readTree(jsonData);

        if (rootNode.has("error_type")) {
            String errorType = rootNode.get("error_type").asText();
            String errorMessage = rootNode.has("message") ? rootNode.get("message").asText() : "Unknown error";
            logger.error("parseJsonToPostTournamentList - API Error: " + errorType + " - " + errorMessage);
            throw new ApiException("API Error: " + errorType + " - " + errorMessage);
        }

        List<PostTournament> tournamentList = new ArrayList<>();

        if (rootNode.isArray()) {
            for (JsonNode node : rootNode) {
                tournamentList.add(objectMapper.treeToValue(node, PostTournament.class));
            }
        } else if (rootNode.isObject()) {
            tournamentList.add(objectMapper.treeToValue(rootNode, PostTournament.class));
        } else {
            logger.error("parseJsonToPostTournamentList - Unexpected Tournaments JSON structure: " + jsonData);
            throw new IllegalArgumentException("Unexpected JSON structure for tournaments");
        }

        logger.info("parseJsonToPostTournamentList - Successfully parsed " + tournamentList.size()
                + " PostTournament objects.");
        return tournamentList;
    }

    private List<PostSubstage> parseJsonToPostSubstageList(String jsonData) throws Exception {
        logger.info("parseJsonToPostSubstageList - Parsing JSON data.");
        JsonNode rootNode = objectMapper.readTree(jsonData);

        if (rootNode.has("error_type")) {
            String errorType = rootNode.get("error_type").asText();
            String errorMessage = rootNode.has("message") ? rootNode.get("message").asText() : "Unknown error";
            logger.error("parseJsonToPostSubstageList - API Error: " + errorType + " - " + errorMessage);
            throw new ApiException("API Error: " + errorType + " - " + errorMessage);
        }

        List<PostSubstage> substageList = new ArrayList<>();

        if (rootNode.isArray()) {
            for (JsonNode node : rootNode) {
                substageList.add(objectMapper.treeToValue(node, PostSubstage.class));
            }
        } else if (rootNode.isObject()) {
            substageList.add(objectMapper.treeToValue(rootNode, PostSubstage.class));
        } else {
            logger.error("parseJsonToPostSubstageList - Unexpected Substages JSON structure: " + jsonData);
            throw new IllegalArgumentException("Unexpected JSON structure for substages");
        }

        logger.info(
                "parseJsonToPostSubstageList - Successfully parsed " + substageList.size() + " PostSubstage objects.");
        return substageList;
    }

    private List<PostCaster> parseJsonToPostCasterList(String jsonData) throws Exception {
        logger.info("parseJsonToPostCasterList - Parsing JSON data.");
        JsonNode rootNode = objectMapper.readTree(jsonData);

        if (rootNode.has("error_type")) {
            String errorType = rootNode.get("error_type").asText();
            String errorMessage = rootNode.has("message") ? rootNode.get("message").asText() : "Unknown error";
            logger.error("parseJsonToPostCasterList - API Error: " + errorType + " - " + errorMessage);
            throw new ApiException("API Error: " + errorType + " - " + errorMessage);
        }

        List<PostCaster> casterList = new ArrayList<>();

        if (rootNode.isArray()) {
            for (JsonNode node : rootNode) {
                casterList.add(objectMapper.treeToValue(node, PostCaster.class));
            }
        } else if (rootNode.isObject()) {
            casterList.add(objectMapper.treeToValue(rootNode, PostCaster.class));
        } else {
            logger.error("parseJsonToPostCasterList - Unexpected Casters JSON structure: " + jsonData);
            throw new IllegalArgumentException("Unexpected JSON structure for casters");
        }

        logger.info("parseJsonToPostCasterList - Successfully parsed " + casterList.size() + " PostCaster objects.");
        return casterList;
    }

    private Series convertToThriftSeries(PostSeries postSeries) {
        logger.info("convertToThriftSeries - Converting PostSeries with ID: " + postSeries.id);
        Series series = new Series();
        series.setId(postSeries.id);
        series.setTitle(postSeries.title);
        series.setStart(toEpochMilli(postSeries.start));
        series.setEnd(toEpochMilli(postSeries.end));
        series.setPostponedFrom(toEpochMilli(postSeries.postponedFrom));
        series.setDeletedAt(toEpochMilli(postSeries.deletedAt));
        series.setLifecycle(postSeries.lifecycle);
        series.setTier(postSeries.tier);
        series.setBestOf(postSeries.bestOf);
        series.setChainIds(postSeries.getChainIds());
        series.setStreamed(postSeries.streamed);
        series.setBracketPosition(convertBracketPosition(postSeries.bracketPosition));
        series.setParticipants(
                postSeries.participants.stream().map(this::convertParticipant).collect(Collectors.toList()));
        series.setTournamentId(postSeries.getTournamentId());
        series.setSubstageId(postSeries.getSubstageId());
        series.setGameId(postSeries.getGameId());
        series.setMatchIds(postSeries.getMatchIds());

        // Convert casters to Thrift Caster objects with only IDs
        series.setCasters(
                postSeries.casters.stream()
                        .map(this::convertCasterReference)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList()));

        // Convert broadcasters to Thrift Broadcaster objects
        series.setBroadcasters(
                postSeries.broadcasters.stream()
                        .map(this::convertBroadcaster)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList()));

        series.setHasIncidentReport(postSeries.hasIncidentReport);
        series.setCoverage(convertCoverage(postSeries.coverage));
        series.setFormatBestOf(postSeries.format != null ? postSeries.bestOf : 0);
        series.setGameVersion(convertGameVersion(postSeries.gameVersion));
        series.setResourceVersion(postSeries.resourceVersion);
        series.setCreatedAt(toEpochMilli(postSeries.createdAt));
        series.setUpdatedAt(toEpochMilli(postSeries.updatedAt));

        try {
            logger.info("convertToThriftSeries - Converted Series: " + objectMapper.writeValueAsString(series));
        } catch (JsonProcessingException e) {
            logger.error("JSONProcessing exception during convert to thrift series: ", e);
            e.printStackTrace();
        }
        return series;
    }

    private Match convertToThriftMatch(PostMatch postMatch) {
        logger.info("convertToThriftMatch - Converting PostMatch with ID: " + postMatch.id);
        Match match = new Match();
        match.setId(postMatch.id);
        match.setMapId(postMatch.getMapId());
        match.setLifecycle(postMatch.lifecycle);
        match.setOrder(postMatch.order);
        match.setSeriesId(postMatch.getSeriesId());
        match.setDeletedAt(postMatch.deletedAt != null ? postMatch.deletedAt.toEpochMilli() : 0);
        match.setGameId(postMatch.getGameId());
        match.setParticipants(
                postMatch.participants != null
                        ? postMatch.participants.stream().map(this::convertParticipant).collect(Collectors.toList())
                        : Collections.emptyList());
        match.setCoverage(convertCoverage(postMatch.coverage));
        match.setResourceVersion(postMatch.resourceVersion);

        try {
            logger.info("convertToThriftMatch - Converted Match: " + objectMapper.writeValueAsString(match));
        } catch (JsonProcessingException e) {
            logger.error("JSONProcessing exception during convert to thrift match: ", e);
            e.printStackTrace();
        }
        return match;
    }

    private Roster convertToThriftRoster(PostRoster postRoster) {
        logger.info("convertToThriftRoster - Converting PostRoster with ID: " + postRoster.id);
        Roster roster = new Roster();
        roster.setId(postRoster.id);
        roster.setTeamId(postRoster.team != null ? postRoster.team.id : 0);
        if (postRoster.lineUp != null && postRoster.lineUp.players != null) {
            roster.setPlayerIds(postRoster.lineUp.players.stream()
                    .map(p -> p.id)
                    .collect(Collectors.toList()));
        } else {
            roster.setPlayerIds(Collections.emptyList());
        }
        roster.setGameId(postRoster.game != null ? postRoster.game.id : 0);

        try {
            logger.info("convertToThriftRoster - Converted Roster: " + objectMapper.writeValueAsString(roster));
        } catch (JsonProcessingException e) {
            logger.error("JSONProcessing exception during convert to thrift roster: ", e);
            e.printStackTrace();
        }
        return roster;
    }

    private Team convertToThriftTeam(PostTeam postTeam) {
        logger.info("convertToThriftTeam - Converting PostTeam with ID: " + postTeam.id);
        Team team = new Team();
        team.setId(postTeam.id);
        team.setName(postTeam.name);
        team.setAbbreviation(postTeam.abbreviation);
        team.setAlsoKnownAs(postTeam.alsoKnownAs);
        team.setDeletedAt(postTeam.deletedAt != null ? postTeam.deletedAt.toEpochMilli() : 0);
        team.setActive(postTeam.active);
        team.setImages(convertImages(postTeam.images));
        team.setRegion(convertRegion(postTeam.region));
        team.setSocialMediaAccounts(convertSocialMediaAccounts(postTeam.socialMediaAccounts));
        team.setStandingRoster(convertStandingRoster(postTeam.standingRoster));
        team.setGameId(postTeam.game != null ? postTeam.game.id : 0);
        team.setOrganizationId(postTeam.organization != null ? postTeam.organization.id : 0);
        team.setResourceVersion(postTeam.resourceVersion);

        try {
            logger.info("convertToThriftTeam - Converted Team: " + objectMapper.writeValueAsString(team));
        } catch (JsonProcessingException e) {
            logger.error("JSONProcessing exception during convert to thrift team: ", e);
            e.printStackTrace();
        }
        return team;
    }

    private Player convertToThriftPlayer(PostPlayer postPlayer) {
        logger.info("convertToThriftPlayer - Converting PostPlayer with ID: " + postPlayer.id);
        Player player = new Player();
        player.setId(postPlayer.id);
        player.setFirstName(postPlayer.firstName);
        player.setLastName(postPlayer.lastName);
        player.setNickName(postPlayer.nickName);
        player.setAlsoKnownAs(postPlayer.alsoKnownAs);
        player.setAge(convertAge(postPlayer.age));
        player.setDeletedAt(postPlayer.deletedAt != null ? postPlayer.deletedAt.toEpochMilli() : 0);
        player.setActive(postPlayer.active);
        player.setImages(convertImages(postPlayer.images));
        player.setRegion(convertRegion(postPlayer.region));
        player.setGameId(postPlayer.game != null ? postPlayer.game.id : 0);
        player.setRaceId(postPlayer.race != null ? postPlayer.race.id : 0);
        player.setRoleId(postPlayer.role != null ? postPlayer.role.id : 0);
        player.setTeamIds(convertTeamIds(postPlayer.teams));
        player.setSocialMediaAccounts(convertSocialMediaAccounts(postPlayer.socialMediaAccounts));
        player.setResourceVersion(postPlayer.resourceVersion);

        try {
            logger.info("convertToThriftPlayer - Converted Player: " + objectMapper.writeValueAsString(player));
        } catch (JsonProcessingException e) {
            logger.error("JSONProcessing exception during convert to thrift player: ", e);
            e.printStackTrace();
        }
        return player;
    }

    private LolMatchSummary convertToThriftLolMatchSummary(PostLolMatchSummary postSummary) {
        logger.info("convertToThriftLolMatchSummary - Converting PostLolMatchSummary.");
        LolMatchSummary summary = new LolMatchSummary();

        if (postSummary == null) {
            logger.error("convertToThriftLolMatchSummary - postSummary is null.");
            return summary;
        }

        int matchId = (postSummary.match != null) ? postSummary.match.id : 0;
        summary.setId(matchId);
        summary.setMatch((postSummary.match != null) ? convertToThriftLolMatch(postSummary.match) : null);
        summary.setTeams((postSummary.teams != null) ? convertToThriftLolTeams(postSummary.teams, matchId) : null);
        summary.setPits((postSummary.pits != null) ? convertToThriftLolPits(postSummary.pits) : null);
        summary.setLatestEventsChannelIndex(Math.max(postSummary.latest_events_channel_index, 0L));
        summary.setLatestStatesChannelIndex(Math.max(postSummary.latest_states_channel_index, 0L));
        summary.setTimestamp(
                (postSummary.timestamp != null) ? postSummary.timestamp.toString() : Instant.now().toString());

        Set<Integer> assetIds = new HashSet<>();
        collectAssetIds(postSummary, assetIds);
        summary.setAssetIds(assetIds);

        try {
            logger.info("convertToThriftLolMatchSummary - Converted LolMatchSummary: "
                    + objectMapper.writeValueAsString(summary));
        } catch (JsonProcessingException e) {
            logger.error("JSONProcessing exception during convert to thrift lol match summary: ", e);
            e.printStackTrace();
        }
        return summary;
    }

    private Asset convertToThriftAsset(PostAsset postAsset) {
        logger.info("convertToThriftAsset - Converting PostAsset with ID: " + postAsset.id);
        Asset asset = new Asset();
        asset.setId(postAsset.id);
        asset.setName(postAsset.name);
        asset.setGame(convertToThriftGame(postAsset.game));
        asset.setCategory(postAsset.category);
        asset.setSubcategory(postAsset.subcategory);
        asset.setExternalId(postAsset.external_id);
        asset.setImages(convertImages(postAsset.images));

        try {
            logger.info("convertToThriftAsset - Converted Asset: " + objectMapper.writeValueAsString(asset));
        } catch (JsonProcessingException e) {
            logger.error("JSONProcessing exception during convert to thrift asset: ", e);
            e.printStackTrace();
        }
        return asset;
    }

    private Tournament convertToThriftTournament(PostTournament postTournament) {
        logger.info("convertToThriftTournament - Converting PostTournament with ID: " + postTournament.id);
        Tournament tournament = new Tournament();
        tournament.setId(postTournament.id);
        tournament.setTitle(postTournament.title);
        tournament.setShortTitle(postTournament.shortTitle);
        tournament.setTier(postTournament.tier);
        tournament.setCopy(convertTournamentCopy(postTournament.copy));
        tournament.setLinks(convertTournamentLinks(postTournament.links));
        tournament.setStart(postTournament.start != null ? postTournament.start.toEpochMilli() : 0);
        tournament.setEnd(postTournament.end != null ? postTournament.end.toEpochMilli() : 0);
        tournament.setGameId(postTournament.game != null ? postTournament.game.id : 0);
        tournament.setStringPrizePool(convertStringPrizePool(postTournament.stringPrizePool));
        tournament.setLocation(convertTournamentLocation(postTournament.location));
        tournament.setDeletedAt(postTournament.deletedAt != null ? postTournament.deletedAt.toEpochMilli() : 0);
        tournament.setImages(convertImages(postTournament.images));
        tournament.setStageIds(convertStageIds(postTournament.stages));
        tournament.setCasters(convertTournamentCasters(postTournament.casters));
        tournament.setBroadcasters(convertTournamentBroadcasters(postTournament.broadcasters));
        tournament.setDefaults(convertTournamentDefaults(postTournament.defaults));
        tournament.setCoverage(convertCoverage(postTournament.coverage));
        tournament.setResourceVersion(postTournament.resourceVersion);

        try {
            logger.info(
                    "convertToThriftTournament - Converted Tournament: " + objectMapper.writeValueAsString(tournament));
        } catch (JsonProcessingException e) {
            logger.error("JSONProcessing exception during convert to thrift tournament: ", e);
            e.printStackTrace();
        }
        return tournament;
    }

    private Substage convertToThriftSubstage(PostSubstage postSubstage) {
        logger.info("convertToThriftSubstage - Converting PostSubstage with ID: " + postSubstage.id);
        Substage substage = new Substage();
        substage.setId(postSubstage.id);
        substage.setStageId(postSubstage.stage != null ? postSubstage.stage.id : 0);
        substage.setTitle(postSubstage.title);
        substage.setTier(postSubstage.tier);
        substage.setType(postSubstage.type);
        substage.setPhase(postSubstage.phase);
        substage.setDefaultSeriesFormat(convertFormat(postSubstage.defaultSeriesFormat));
        substage.setGameId(postSubstage.game != null ? postSubstage.game.id : 0);
        substage.setTournamentId(postSubstage.tournament != null ? postSubstage.tournament.id : 0);
        substage.setOrder(postSubstage.order);
        substage.setRosterIds(convertRosterIds(postSubstage.rosters));
        substage.setStart(postSubstage.start != null ? postSubstage.start.toEpochMilli() : 0);
        substage.setDeletedAt(postSubstage.deletedAt != null ? postSubstage.deletedAt.toEpochMilli() : 0);
        substage.setStandings(convertStandings(postSubstage.standings));
        substage.setRules(convertSubstageRules(postSubstage.rules));
        substage.setDefaults(convertSubstageDefaults(postSubstage.defaults));
        substage.setFormat(convertSubstageFormat(postSubstage.format));
        substage.setCoverage(convertCoverage(postSubstage.coverage));
        substage.setResourceVersion(postSubstage.resourceVersion);

        try {
            logger.info("convertToThriftSubstage - Converted Substage: " + objectMapper.writeValueAsString(substage));
        } catch (JsonProcessingException e) {
            logger.error("JSONProcessing exception during convert to thrift substage: ", e);
            e.printStackTrace();
        }
        return substage;
    }

    private Caster convertToThriftCaster(PostCaster postCaster) {
        logger.info("convertToThriftCaster - Converting PostCaster with ID: " + postCaster.id);
        Caster caster = new Caster();
        caster.setId(postCaster.id);
        caster.setDisplayName(postCaster.displayName);
        caster.setUsername(postCaster.username);
        caster.setGameId(postCaster.game != null ? postCaster.game.id : 0);
        caster.setDeletedAt(postCaster.deletedAt != null ? postCaster.deletedAt.toEpochMilli() : 0);
        caster.setPlatform(convertStreamingPlatform(postCaster.platform));
        caster.setStream(convertStream(postCaster.stream));
        caster.setRegion(convertRegion(postCaster.region));

        try {
            logger.info("convertToThriftCaster - Converted Caster: " + objectMapper.writeValueAsString(caster));
        } catch (JsonProcessingException e) {
            logger.error("JSONProcessing exception during convert to thrift caster: ", e);
            e.printStackTrace();
        }
        return caster;
    }

    private Caster convertCasterReference(PostCasterInfo casterRef) {
        if (casterRef == null || casterRef.caster == null || casterRef.caster.id == null) {
            logger.warn("convertCasterReference - Invalid caster reference.");
            return null;
        }

        // Create a Caster object with only the ID set. Full details will be fetched
        // later.
        Caster caster = new Caster();
        caster.setId(casterRef.caster.id);
        // Other fields will remain default or null

        return caster;
    }

    private List<Integer> convertRosterIds(List<PostRosterInfo> postRosters) {
        if (postRosters == null) {
            return null;
        }
        return postRosters.stream()
                .map(rosterInfo -> rosterInfo.id)
                .collect(Collectors.toList());
    }

    private Format convertFormat(PostFormat postFormat) {
        if (postFormat == null) {
            return null;
        }
        Format format = new Format();
        format.setBestOf(postFormat.bestOf);
        return format;
    }

    // Utility method to handle Instant to epoch milliseconds conversion
    private long toEpochMilli(Instant instant) {
        return instant != null ? instant.toEpochMilli() : 0;
    }

    // eSports getters
    public CompletableFuture<GetMatch> getMatch(int matchId) {
        logger.info("getMatch - Fetching match with ID: " + matchId);
        return getMatchFromMatchId.invokeAsync(matchId)
                .thenCompose(match -> {
                    if (match == null) {
                        logger.warn("getMatch - No match found with ID: " + matchId);
                        return CompletableFuture.completedFuture(null);
                    }

                    List<CompletableFuture<GetParticipant>> participantFutures = match.getParticipants().stream()
                            .map(this::fetchFullParticipantData)
                            .collect(Collectors.toList());

                    final CompletableFuture<GetLolMatchSummary> lolSummaryFuture = (match.getGameId() == 2)
                            ? getLolMatchSummary(match.getId())
                            : CompletableFuture.completedFuture(null);

                    CompletableFuture<Void> allParticipantsFuture = CompletableFuture.allOf(
                            participantFutures.toArray(new CompletableFuture[0]));

                    return CompletableFuture.allOf(allParticipantsFuture, lolSummaryFuture)
                            .thenApply(v -> {
                                GetMatch getMatch = new GetMatch(match);
                                getMatch.participants = participantFutures.stream()
                                        .map(CompletableFuture::join)
                                        .collect(Collectors.toList());

                                GetLolMatchSummary lolSummary = lolSummaryFuture.join();
                                if (lolSummary != null) {
                                    // **Integrate the match.clock from LolMatchSummary**
                                    if (lolSummary.match != null && lolSummary.match.clock != null) {
                                        GetLolMatchClock getClock = lolSummary.match.clock;
                                        getMatch.clock = getClock;
                                        logger.info(
                                                "getMatch - Integrated match.clock from LolMatchSummary for matchId: "
                                                        + matchId);
                                    } else {
                                        logger.warn("getMatch - LolMatchSummary match or clock is null for matchId: "
                                                + matchId);
                                    }

                                    mergeStatsIntoParticipants(getMatch.participants, lolSummary);
                                    logger.info(
                                            "getMatch - Merged LoL stats into participants for matchId: " + matchId);
                                }

                                logger.info("getMatch - Successfully fetched and constructed GetMatch for matchId: "
                                        + matchId);
                                return getMatch;
                            });
                });
    }

    public CompletableFuture<GetSeries> getSeries(int seriesId) {
        logger.info("getSeries - Fetching series with ID: " + seriesId);
        return getSeriesFromSeriesId.invokeAsync(seriesId)
                .thenCompose(series -> {
                    if (series == null) {
                        logger.warn("getSeries - No series found with ID: " + seriesId);
                        return CompletableFuture.completedFuture(null);
                    }

                    return processSingleSeries(series);
                });
    }

    public CompletableFuture<GetTeam> getTeam(int teamId) {
        logger.info("getTeam - Fetching team with ID: " + teamId);
        return getTeamFromTeamId.invokeAsync(teamId)
                .thenApply(team -> {
                    if (team != null) {
                        logger.info("getTeam - Successfully fetched team with ID: " + teamId);
                        return new GetTeam(team);
                    } else {
                        logger.warn("getTeam - No team found with ID: " + teamId);
                        return null;
                    }
                });
    }

    public CompletableFuture<List<Integer>> getSeriesIdsForTeam(int teamId) {
        return getSeriesIdsFromTeamId.invokeAsync(teamId)
                .thenApply(seriesIds -> {
                    if (seriesIds != null) {
                        return seriesIds;
                    } else {
                        return Collections.emptyList();
                    }
                });
    }

    public CompletableFuture<GetPlayer> getPlayer(int playerId) {
        logger.info("getPlayer - Fetching player with ID: " + playerId);
        return getPlayerFromPlayerId.invokeAsync(playerId)
                .thenApply(player -> {
                    if (player != null) {
                        logger.info("getPlayer - Successfully fetched player with ID: " + playerId);
                        return new GetPlayer(player);
                    } else {
                        logger.warn("getPlayer - No player found with ID: " + playerId);
                        return null;
                    }
                });
    }

    public CompletableFuture<List<GetPlayer>> getPlayersFromRosterId(int rosterId) {
        logger.info("getPlayersFromRosterId - Fetching players from rosterId: " + rosterId);
        return getRosterFromRosterId.invokeAsync(rosterId)
                .thenCompose(roster -> {
                    if (roster == null) {
                        // Roster does not exist
                        logger.warn("getPlayersFromRosterId - Roster not found for rosterId: " + rosterId);
                        return CompletableFuture
                                .failedFuture(new ResponseStatusException(HttpStatus.NOT_FOUND, "Roster not found"));
                    }
                    if (roster.getPlayerIds() != null && !roster.getPlayerIds().isEmpty()) {
                        List<CompletableFuture<GetPlayer>> playerFutures = roster.getPlayerIds().stream()
                                .map(this::getPlayer)
                                .collect(Collectors.toList());
                        return CompletableFuture.allOf(playerFutures.toArray(new CompletableFuture[0]))
                                .thenApply(v -> playerFutures.stream()
                                        .map(CompletableFuture::join)
                                        .collect(Collectors.toList()));
                    } else {
                        // Roster exists but has no players
                        logger.warn("getPlayersFromRosterId - No players found for rosterId: " + rosterId);
                        return CompletableFuture.completedFuture(Collections.emptyList());
                    }
                });
    }

    // TODO: Maybe implement schedule but maybe take out
    public CompletableFuture<GetPlayer> getPlayerWithLolStats(int playerId) {
        logger.info("getPlayerWithLolStats - Initiating fetch for playerId: {}", playerId);

        CompletableFuture<Player> playerFuture = getPlayerFromPlayerId.invokeAsync(playerId);
        CompletableFuture<LolPlayerAggStats> aggStatsFuture = getLolPlayerAggStatsFromPlayerId.invokeAsync(playerId);
        CompletableFuture<List<LolPlayerSummary>> seasonStatsFuture = getLolPlayerSeasonStatsFromPlayerId
                .invokeAsync(playerId);
        // CompletableFuture<List<Integer>> seriesIdsFuture =
        // getSeriesIdsForTeam(teamId);

        return CompletableFuture.allOf(playerFuture, aggStatsFuture, seasonStatsFuture /* , seriesIdsFuture */ )
                .thenCompose(v -> {
                    Player player = playerFuture.join();
                    LolPlayerAggStats aggStats = aggStatsFuture.join();
                    List<LolPlayerSummary> seasonStats = seasonStatsFuture.join();
                    // List<Integer> seriesIds = seriesIdsFuture.join();

                    if (player == null || aggStats == null) {
                        logger.warn("getPlayerWithLolStats - No Player or Aggregated Stats found with ID: {}",
                                playerId);
                        return CompletableFuture.completedFuture(null);
                    }

                    // Collect asset IDs from seasonStats
                    Set<Integer> assetIds = seasonStats.stream()
                            .filter(Objects::nonNull)
                            .map(this::collectPlayerAssetIds)
                            .flatMap(Set::stream)
                            .collect(Collectors.toSet());

                    List<CompletableFuture<Asset>> assetFutures = assetIds.stream()
                            .map(this::getAsset)
                            .collect(Collectors.toList());

                    CompletableFuture<Void> assetsFuture = CompletableFuture
                            .allOf(assetFutures.toArray(new CompletableFuture[0]));

                    return CompletableFuture.allOf(assetsFuture)
                            .thenApply(x -> {
                                Map<Integer, GetAsset> assetMap = assetFutures.stream()
                                        .map(CompletableFuture::join)
                                        .filter(Objects::nonNull)
                                        .collect(Collectors.toMap(Asset::getId, GetAsset::new));

                                // Create and return a GetTeam object enriched with series data
                                GetPlayer getPlayer = new GetPlayer(player, aggStats, seasonStats, assetMap);
                                // getPlayer.schedule = seriesIds;

                                logger.info(
                                        "getPlayerWithLolStats - Successfully constructed GetPlayer for playerId: {}",
                                        playerId);
                                return getPlayer;
                            });
                });
    }

    public CompletableFuture<GetTeam> getTeamWithLolStats(int teamId) {
        logger.info("getTeamWithLolStats - Initiating fetch for teamId: {}", teamId);

        CompletableFuture<Team> teamFuture = getTeamFromTeamId.invokeAsync(teamId);
        CompletableFuture<LolTeamAggStats> aggStatsFuture = getLolTeamAggStatsFromTeamId.invokeAsync(teamId);
        CompletableFuture<List<LolTeamSummary>> seasonStatsFuture = getLolTeamSeasonStatsFromTeamId.invokeAsync(teamId);
        CompletableFuture<List<Integer>> seriesIdsFuture = getSeriesIdsForTeam(teamId);

        return CompletableFuture.allOf(teamFuture, aggStatsFuture, seasonStatsFuture, seriesIdsFuture)
                .thenCompose(v -> {
                    Team team = teamFuture.join();
                    LolTeamAggStats aggStats = aggStatsFuture.join();
                    List<LolTeamSummary> seasonStats = seasonStatsFuture.join();
                    List<Integer> seriesIds = seriesIdsFuture.join();

                    if (team == null || aggStats == null) {
                        logger.warn("getTeamWithLolStats - No Team or Aggregated Stats found with ID: {}", teamId);
                        return CompletableFuture.completedFuture(null);
                    }

                    // Collect asset IDs from seasonStats
                    Set<Integer> assetIds = seasonStats.stream()
                            .filter(Objects::nonNull)
                            .map(this::collectTeamAssetIds)
                            .flatMap(Set::stream)
                            .collect(Collectors.toSet());

                    List<CompletableFuture<Asset>> assetFutures = assetIds.stream()
                            .map(this::getAsset)
                            .collect(Collectors.toList());

                    CompletableFuture<Void> assetsFuture = CompletableFuture
                            .allOf(assetFutures.toArray(new CompletableFuture[0]));

                    return CompletableFuture.allOf(assetsFuture)
                            .thenApply(x -> {
                                Map<Integer, GetAsset> assetMap = assetFutures.stream()
                                        .map(CompletableFuture::join)
                                        .filter(Objects::nonNull)
                                        .collect(Collectors.toMap(Asset::getId, GetAsset::new));

                                // Create and return a GetTeam object enriched with series data
                                GetTeam getTeam = new GetTeam(team, aggStats, seasonStats, assetMap);
                                getTeam.schedule = seriesIds;

                                logger.info("getTeamWithLolStats - Successfully constructed GetTeam for teamId: {}",
                                        teamId);
                                return getTeam;
                            });
                });
    }

    public CompletableFuture<List<Integer>> getAllTeamIds() {
        return getAllTeamIds.invokeAsync();
    }

    public CompletableFuture<GetTeam> getTeamWithAggStats(int teamId) {
        logger.info("getTeamWithAggStats - Initiating fetch for teamId: {}", teamId);

        CompletableFuture<Team> teamFuture = getTeamFromTeamId.invokeAsync(teamId);
        CompletableFuture<LolTeamAggStats> aggStatsFuture = getLolTeamAggStatsFromTeamId.invokeAsync(teamId);

        return CompletableFuture.allOf(teamFuture, aggStatsFuture)
                .thenApply(v -> {
                    Team team = teamFuture.join();
                    LolTeamAggStats aggStats = aggStatsFuture.join();

                    if (team == null) {
                        logger.warn("getTeamWithAggStats - No Team found with ID: {}", teamId);
                        return null;
                    }

                    if (aggStats == null) {
                        logger.warn(
                                "getTeamWithAggStats - No Aggregated Stats found for teamId: {}. Initializing with default values.",
                                teamId);
                        aggStats = createDefaultTeamAggStats(teamId);
                    }

                    // Construct GetTeam without seasonStats and assetMap
                    GetTeam getTeam = new GetTeam(team, aggStats, Collections.emptyList(), Collections.emptyMap());
                    logger.info("getTeamWithAggStats - Successfully constructed GetTeam with agg stats for teamId: {}",
                            teamId);
                    return getTeam;
                });
    }

    public CompletableFuture<List<GetTeam>> getAllTeamsWithAggStats() {
        return getAllTeamIds()
                .thenCompose(teamIds -> {
                    // Fetch team and agg stats in parallel for all team IDs
                    List<CompletableFuture<GetTeam>> teamFutures = teamIds.stream()
                            .map(this::getTeamWithAggStats)
                            .collect(Collectors.toList());

                    // Wait for all futures to complete
                    return CompletableFuture.allOf(teamFutures.toArray(new CompletableFuture[0]))
                            .thenApply(v -> teamFutures.stream()
                                    .map(CompletableFuture::join)
                                    .filter(Objects::nonNull)
                                    .collect(Collectors.toList()));
                })
                .exceptionally(e -> {
                    logger.error("Error fetching all teams with aggregated stats: {}", e.getMessage());
                    return Collections.emptyList();
                });
    }

    public CompletableFuture<List<Integer>> getAllPlayerIds() {
        return getAllPlayerIds.invokeAsync();
    }

    public CompletableFuture<GetPlayer> getPlayerWithAggStats(int playerId) {
        logger.info("getPlayerWithAggStats - Initiating fetch for playerId: {}", playerId);

        CompletableFuture<Player> playerFuture = getPlayerFromPlayerId.invokeAsync(playerId);
        CompletableFuture<LolPlayerAggStats> aggStatsFuture = getLolPlayerAggStatsFromPlayerId.invokeAsync(playerId);

        return CompletableFuture.allOf(playerFuture, aggStatsFuture)
                .thenApply(v -> {
                    Player player = playerFuture.join();
                    LolPlayerAggStats aggStats = aggStatsFuture.join();

                    if (player == null) {
                        logger.warn("getPlayerWithAggStats - No Player found with ID: {}", playerId);
                        return null;
                    }

                    if (aggStats == null) {
                        logger.warn(
                                "getPlayerWithAggStats - No Aggregated Stats found for playerId: {}. Initializing with default values.",
                                playerId);
                        aggStats = createDefaultPlayerAggStats(playerId);
                    }

                    // Construct GetPlayer without seasonStats and assetMap
                    GetPlayer getPlayer = new GetPlayer(player, aggStats, Collections.emptyList(),
                            Collections.emptyMap());
                    logger.info(
                            "getPlayerWithAggStats - Successfully constructed GetPlayer with agg stats for playerId: {}",
                            playerId);
                    return getPlayer;
                });
    }

    public CompletableFuture<List<GetPlayer>> getAllPlayersWithAggStats() {
        return getAllPlayerIds()
                .thenCompose(playerIds -> {
                    // Fetch player and agg stats in parallel for all team IDs
                    List<CompletableFuture<GetPlayer>> playerFutures = playerIds.stream()
                            .map(this::getPlayerWithAggStats)
                            .collect(Collectors.toList());

                    // Wait for all futures to complete
                    return CompletableFuture.allOf(playerFutures.toArray(new CompletableFuture[0]))
                            .thenApply(v -> playerFutures.stream()
                                    .map(CompletableFuture::join)
                                    .filter(Objects::nonNull)
                                    .collect(Collectors.toList()));
                })
                .exceptionally(e -> {
                    logger.error("Error fetching all players with aggregated stats: {}", e.getMessage());
                    return Collections.emptyList();
                });
    }

    public CompletableFuture<List<GetSeries>> getSeriesSchedule(long startTime, long endTime) {
        logger.info("getSeriesSchedule - Fetching series schedule from " + startTime + " to " + endTime);
        return getSeriesFromStartTime.invokeAsync(startTime, endTime)
                .thenCompose(result -> {
                    @SuppressWarnings("unchecked")
                    Map<Long, Map<Integer, Series>> aggregatedResult = (Map<Long, Map<Integer, Series>>) result;

                    TreeMap<Long, Map<Integer, Series>> sortedResult = new TreeMap<>(aggregatedResult);
                    logger.info("getSeriesSchedule - Aggregated and sorted series data.");

                    List<Series> seriesList = new ArrayList<>();
                    for (Map<Integer, Series> innerMap : sortedResult.values()) {
                        seriesList.addAll(innerMap.values());
                    }

                    logger.info("getSeriesSchedule - Total Series fetched: " + seriesList.size());
                    return processSeriesList(seriesList);
                });
    }

    private LolTeamAggStats createDefaultTeamAggStats(int teamId) {
        LolTeamAggStats defaultStats = new LolTeamAggStats();
        defaultStats.setId(teamId);
        defaultStats.setTotalMatches(0);
        defaultStats.setTotalWins(0);
        defaultStats.setTotalLosses(0);
        defaultStats.setTotalScore(0);
        defaultStats.setTotalGoldEarned(0);
        defaultStats.setTotalTurretsDestroyed(0);
        defaultStats.setTotalInhibitorsDestroyed(0);
        defaultStats.setAverageScore(0.0);
        defaultStats.setAverageGoldEarned(0.0);
        defaultStats.setAverageTurretsDestroyed(0.0);
        defaultStats.setAverageInhibitorsDestroyed(0.0);
        defaultStats.setCurrentWinStreak(0);
        defaultStats.setTotalDragonKills(0);
        defaultStats.setTotalBaronKills(0);
        defaultStats.setTotalHeraldKills(0);
        defaultStats.setTotalVoidGrubKills(0);
        defaultStats.setAverageDragonKills(0.0);
        defaultStats.setAverageBaronKills(0.0);
        defaultStats.setAverageHeraldKills(0.0);
        defaultStats.setAverageVoidGrubKills(0.0);
        defaultStats.setTotalSeries(0);
        defaultStats.setTotalSeriesWins(0);
        defaultStats.setTotalSeriesLosses(0);
        return defaultStats;
    }

    private LolPlayerAggStats createDefaultPlayerAggStats(int playerId) {
        LolPlayerAggStats defaultStats = new LolPlayerAggStats();
        defaultStats.setId(playerId);
        return defaultStats;
    }

    private CompletableFuture<List<GetSeries>> processSeriesList(List<Series> seriesList) {
        logger.info("processSeriesList - Processing list of Series. Count: " + seriesList.size());

        if (seriesList.isEmpty()) {
            logger.warn("processSeriesList - Received an empty series list.");
            return CompletableFuture.completedFuture(Collections.emptyList());
        }

        // Collect IDs of related entities across all series
        Set<Integer> tournamentIds = seriesList.stream()
                .map(Series::getTournamentId)
                .filter(id -> id != 0) // Assuming 0 is an invalid ID
                .collect(Collectors.toSet());

        Set<Integer> substageIds = seriesList.stream()
                .map(Series::getSubstageId)
                .filter(id -> id != 0)
                .collect(Collectors.toSet());

        Set<Integer> casterIds = seriesList.stream()
                .flatMap(series -> series.getCasters().stream())
                .map(Caster::getId)
                .filter(id -> id != 0)
                .collect(Collectors.toSet());

        // Fetch related entities asynchronously
        CompletableFuture<Map<Integer, Tournament>> tournamentsFuture = fetchTournamentsByIds(tournamentIds);
        CompletableFuture<Map<Integer, Substage>> substagesFuture = fetchSubstagesByIds(substageIds);
        CompletableFuture<Map<Integer, Caster>> castersFuture = fetchCastersByIds(casterIds);

        // Wait for all related entity fetches to complete
        return CompletableFuture.allOf(tournamentsFuture, substagesFuture, castersFuture)
                .thenCompose(v -> {
                    Map<Integer, Tournament> tournaments = tournamentsFuture.join();
                    Map<Integer, Substage> substages = substagesFuture.join();
                    Map<Integer, Caster> castersMap = castersFuture.join();

                    // Step 4: Process each series asynchronously
                    List<CompletableFuture<GetSeries>> futuresList = seriesList.stream()
                            .map(series -> {
                                // Fetch participant data for the current series
                                List<CompletableFuture<GetParticipant>> participantFutures = series.getParticipants()
                                        .stream()
                                        .map(this::fetchFullParticipantData)
                                        .collect(Collectors.toList());

                                // Map casters with full details
                                List<GetCaster> getCasters = series.getCasters().stream()
                                        .map(casterRef -> {
                                            Caster fullCaster = castersMap.get(casterRef.getId());
                                            return fullCaster != null ? new GetCaster(fullCaster)
                                                    : new GetCaster(casterRef);
                                        })
                                        .collect(Collectors.toList());

                                // Map broadcasters directly to GetBroadcaster
                                List<GetBroadcaster> getBroadcasters = series.getBroadcasters().stream()
                                        .map(GetBroadcaster::new) // Assuming GetBroadcaster has a constructor that
                                                                  // takes Broadcaster
                                        .collect(Collectors.toList());

                                // Wait for participant data to complete and assemble GetSeries
                                return CompletableFuture.allOf(participantFutures.toArray(new CompletableFuture[0]))
                                        .thenApply(vv -> {
                                            GetSeries getSeries = new GetSeries(series);

                                            // Assign participants
                                            getSeries.participants = participantFutures.stream()
                                                    .map(CompletableFuture::join)
                                                    .collect(Collectors.toList());

                                            // Assign casters with full details
                                            getSeries.casters = getCasters;

                                            // Assign tournament
                                            Tournament tournament = tournaments.get(series.getTournamentId());
                                            getSeries.tournament = tournament != null ? new GetTournament(tournament)
                                                    : null;

                                            // Assign substage
                                            Substage substage = substages.get(series.getSubstageId());
                                            getSeries.substage = substage != null ? new GetSubstage(substage) : null;

                                            // Assign broadcasters
                                            getSeries.broadcasters = getBroadcasters;

                                            logger.info("processSeriesList - Enriched GetSeries for series ID: "
                                                    + series.getId());
                                            return getSeries;
                                        });
                            })
                            .collect(Collectors.toList());

                    // Wait for all series processing to complete and collect results
                    return CompletableFuture.allOf(futuresList.toArray(new CompletableFuture[0]))
                            .thenApply(vv -> futuresList.stream()
                                    .map(CompletableFuture::join)
                                    .collect(Collectors.toList()));
                });
    }

    private CompletableFuture<GetSeries> processSingleSeries(Series series) {
        logger.info("processSingleSeries - Processing single series ID: " + series.getId());

        // Step 1: Create a single-element list containing the series
        List<Series> singleSeriesList = Collections.singletonList(series);

        // Step 2: Delegate processing to processSeriesList
        return processSeriesList(singleSeriesList)
                .thenApply(getSeriesList -> {
                    if (getSeriesList == null || getSeriesList.isEmpty()) {
                        logger.warn("processSingleSeries - No GetSeries returned for series ID: " + series.getId());
                        return null;
                    }
                    // Since it's a single series, return the first element
                    return getSeriesList.get(0);
                });
    }

    public CompletableFuture<List<GetSeries>> getWeekSchedule(long timestamp) {
        // Calculate the start and end timestamps for the week containing the given
        // timestamp
        long startOfWeek = ApolloHelpers.getStartOfWeek(timestamp);
        long endOfWeek = ApolloHelpers.getEndOfWeek(timestamp);

        // Fetch the series schedule within the calculated week range
        return getSeriesSchedule(startOfWeek, endOfWeek);
    }

    private CompletableFuture<GetParticipant> fetchFullParticipantData(Participant participant) {
        if (participant.getRoster() == null) {
            logger.warn("fetchFullParticipantData - Participant's roster is null");
            // Return a GetParticipant object with minimal data
            return CompletableFuture.completedFuture(new GetParticipant(participant));
        }

        logger.info("fetchFullParticipantData - Fetching full data for participant with rosterId: {}",
                participant.getRoster().getId());

        // Proceed to fetch the roster data associated with the participant
        return getRosterFromRosterId.invokeAsync(participant.getRoster().getId())
                .exceptionally(e -> {
                    logger.error("fetchFullParticipantData - Failed to fetch Roster for rosterId {}: {}",
                            participant.getRoster().getId(), e.getMessage());
                    return null;
                })
                .thenCompose(roster -> {
                    if (roster == null) {
                        logger.warn("fetchFullParticipantData - No Roster found for rosterId: {}",
                                participant.getRoster().getId());
                        return CompletableFuture.completedFuture(new GetParticipant(participant));
                    }

                    logger.debug("fetchFullParticipantData - Successfully fetched Roster for rosterId: {}",
                            participant.getRoster().getId());

                    // Create a GetParticipant object from the participant data
                    GetParticipant getParticipant = new GetParticipant(participant);

                    // Asynchronously fetch the team associated with the roster
                    CompletableFuture<GetTeam> teamFuture = getTeam(roster.getTeamId())
                            .exceptionally(e -> {
                                logger.error("fetchFullParticipantData - Failed to fetch Team for teamId {}: {}",
                                        roster.getTeamId(), e.getMessage());
                                return null;
                            });

                    // Asynchronously fetch all players associated with the roster
                    List<CompletableFuture<GetPlayer>> playerFutures = roster.getPlayerIds().stream()
                            .map(this::getPlayer)
                            .collect(Collectors.toList());

                    // Wait for all player data and the team data to be fetched
                    return CompletableFuture.allOf(
                            CompletableFuture.allOf(playerFutures.toArray(new CompletableFuture[0])),
                            teamFuture)
                            .thenApply(v -> {
                                // Set the fetched team data in the GetParticipant object
                                getParticipant.roster.team = teamFuture.join();

                                if (getParticipant.roster.team != null) {
                                    logger.debug(
                                            "fetchFullParticipantData - Enriched GetParticipant with Team data for rosterId: {}",
                                            roster.getId());
                                } else {
                                    logger.warn("fetchFullParticipantData - Team data is null for rosterId: {}",
                                            roster.getId());
                                }

                                // Set the list of fetched players in the GetParticipant object
                                getParticipant.roster.players = playerFutures.stream()
                                        .map(CompletableFuture::join) // Waits for each future to complete
                                        .filter(Objects::nonNull) // Exclude any null players
                                        .collect(Collectors.toList());

                                logger.info(
                                        "fetchFullParticipantData - Successfully enriched GetParticipant for rosterId: {}",
                                        roster.getId());
                                return getParticipant;
                            })
                            .exceptionally(e -> {
                                logger.error(
                                        "fetchFullParticipantData - Error enriching GetParticipant for rosterId {}: {}",
                                        roster.getId(), e.getMessage());
                                return new GetParticipant(participant);
                            });
                });
    }

    private void mergeStatsIntoParticipants(List<GetParticipant> participants, GetLolMatchSummary lolSummary) {
        logger.info("mergeStatsIntoParticipants - Merging LoL stats into participants.");

        // Create a map from roster ID to GetParticipant for quick lookup
        Map<Integer, GetParticipant> rosterIdToParticipant = participants.stream()
                .collect(Collectors.toMap(p -> p.roster.id, p -> p));

        logger.debug("mergeStatsIntoParticipants - Created rosterId to GetParticipant map with size: {}",
                rosterIdToParticipant.size());

        // Retrieve the asset map from the match summary
        Map<Integer, GetAsset> assetMap = lolSummary.getAssetMap();
        logger.debug("mergeStatsIntoParticipants - Asset map size: {}", assetMap.size());

        // Process statistics for the home and away teams
        if (lolSummary.teams != null) {
            if (lolSummary.teams.home != null) {
                processTeamSummary(lolSummary.teams.home, rosterIdToParticipant, assetMap);
            }
            if (lolSummary.teams.away != null) {
                processTeamSummary(lolSummary.teams.away, rosterIdToParticipant, assetMap);
            }
        } else {
            logger.warn("mergeStatsIntoParticipants - LolMatchSummary does not contain teams data.");
        }

        logger.info("mergeStatsIntoParticipants - Successfully merged stats into all participants.");
    }

    private void processTeamSummary(GetLolTeamSummary teamSummary, Map<Integer, GetParticipant> rosterIdToParticipant,
            Map<Integer, GetAsset> assetMap) {
        logger.info("processTeamSummary - Processing team summary for rosterId: {}", teamSummary.roster.id);

        int rosterId = teamSummary.roster.id;

        // Retrieve the corresponding GetParticipant object
        GetParticipant participant = rosterIdToParticipant.get(rosterId);

        if (participant != null && participant.roster != null && participant.roster.team != null) {
            // Enrich the team with match statistics from the team summary
            participant.roster.team.matchStats = new GetLolTeamMatchStats(teamSummary, assetMap);
            logger.debug("processTeamSummary - Set matchStats for teamId: {}", participant.roster.team.id);

            // Create a map from player ID to GetPlayer for quick lookup
            Map<Integer, GetPlayer> playerIdToPlayer = participant.roster.players.stream()
                    .collect(Collectors.toMap(p -> p.id, p -> p));

            // Enrich each player with match statistics and associated assets
            for (GetLolPlayerSummary playerSummary : teamSummary.players) {
                GetPlayer player = playerIdToPlayer.get(playerSummary.id);
                if (player != null) {
                    // Set the match statistics for the player
                    player.matchStats = new GetPlayerMatchStats(playerSummary, assetMap);
                    logger.debug("processTeamSummary - Set matchStats for playerId: {}", player.id);
                } else {
                    logger.warn("processTeamSummary - No GetPlayer found for playerId: {}", playerSummary.id);
                }
            }
        } else {
            logger.warn("processTeamSummary - Participant or team data is incomplete for rosterId: {}", rosterId);
        }
    }

    public CompletableFuture<GetLolMatchSummary> getLolMatchSummary(int matchId) {
        logger.info("getLolMatchSummary - Starting fetch for matchId: {}", matchId);
        long startTime = System.currentTimeMillis();

        // Asynchronously fetch the League of Legends match summary using matchId
        return getLolMatchSummaryFromMatchId.invokeAsync(matchId)
                .exceptionally(e -> {
                    logger.error("getLolMatchSummary - Failed to fetch LoL match summary for matchId {}: {}", matchId,
                            e.getMessage());
                    return null;
                })
                .thenCompose(summary -> {
                    long duration = System.currentTimeMillis() - startTime;
                    logger.info("getLolMatchSummary - Summary fetched in {}ms for matchId: {}", duration, matchId);

                    if (summary == null) {
                        logger.warn("getLolMatchSummary - No summary found for matchId: {}", matchId);
                        return CompletableFuture.completedFuture(null);
                    }

                    logger.debug("getLolMatchSummary - Successfully fetched LoL match summary for matchId: {}",
                            matchId);

                    // Retrieve all asset IDs from the summary
                    Set<Integer> assetIds = summary.getAssetIds();
                    logger.debug("getLolMatchSummary - Collected asset IDs: {}", assetIds);

                    // Asynchronously fetch all assets based on the asset IDs
                    List<CompletableFuture<Asset>> assetFutures = assetIds.stream()
                            .map(this::getAsset)
                            .collect(Collectors.toList());

                    // Wait for all asset fetch operations to complete
                    return CompletableFuture.allOf(assetFutures.toArray(new CompletableFuture[0]))
                            .thenApply(x -> {
                                Map<Integer, GetAsset> assetMap = assetFutures.stream()
                                        .map(CompletableFuture::join)
                                        .filter(Objects::nonNull)
                                        .collect(Collectors.toMap(Asset::getId, GetAsset::new));

                                logger.debug("getLolMatchSummary - Fetched {} assets for matchId: {}", assetMap.size(),
                                        matchId);

                                // Create and return a GetLolMatchSummary object enriched with assets
                                GetLolMatchSummary getLolMatchSummary = new GetLolMatchSummary(summary, assetMap);
                                logger.info(
                                        "getLolMatchSummary - Successfully constructed GetLolMatchSummary for matchId: {}",
                                        matchId);
                                return getLolMatchSummary;
                            })
                            .exceptionally(e -> {
                                logger.error(
                                        "getLolMatchSummary - Error constructing GetLolMatchSummary for matchId {}: {}",
                                        matchId, e.getMessage());
                                return null;
                            });
                });
    }

    public CompletableFuture<Asset> getAsset(int assetId) {
        logger.info("getAsset - Initiating fetch for assetId: {}", assetId);

        // Asynchronously fetch the asset data using the provided assetId
        return getAssetFromAssetId.invokeAsync(assetId)
                .exceptionally(e -> {
                    logger.error("getAsset - Failed to fetch Asset with ID {}: {}", assetId, e.getMessage());
                    return null;
                })
                .thenApply(asset -> {
                    if (asset != null) {
                        logger.debug("getAsset - Successfully fetched Asset: {}", assetId);
                    } else {
                        logger.warn("getAsset - No Asset found with ID: {}", assetId);
                    }
                    return asset;
                });
    }

    private CompletableFuture<Map<Integer, Tournament>> fetchTournamentsByIds(Set<Integer> tournamentIds) {
        if (tournamentIds.isEmpty()) {
            logger.info("fetchTournamentsByIds - No tournament IDs provided to fetch.");
            return CompletableFuture.completedFuture(Collections.emptyMap());
        }

        logger.info("fetchTournamentsByIds - Fetching tournaments with IDs: {}", tournamentIds);

        // Asynchronously fetch each tournament based on its ID
        List<CompletableFuture<Tournament>> futures = tournamentIds.stream()
                .map(id -> getTournamentFromTournamentId.invokeAsync(id)
                        .exceptionally(e -> {
                            logger.error("fetchTournamentsByIds - Failed to fetch Tournament with ID {}: {}", id,
                                    e.getMessage());
                            return null;
                        }))
                .collect(Collectors.toList());

        // Wait for all tournament fetch operations to complete
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> {
                    Map<Integer, Tournament> map = new HashMap<>();
                    for (CompletableFuture<Tournament> future : futures) {
                        Tournament tournament = future.join();
                        if (tournament != null) {
                            map.put(tournament.getId(), tournament);
                            logger.debug("fetchTournamentsByIds - Fetched Tournament: {}", tournament.getId());
                        }
                    }
                    logger.info("fetchTournamentsByIds - Successfully fetched {} tournaments.", map.size());
                    return map;
                })
                .exceptionally(e -> {
                    logger.error("fetchTournamentsByIds - Error while fetching tournaments: {}", e.getMessage());
                    return Collections.emptyMap();
                });
    }

    private CompletableFuture<Map<Integer, Substage>> fetchSubstagesByIds(Set<Integer> substageIds) {
        if (substageIds.isEmpty()) {
            logger.info("fetchSubstagesByIds - No substage IDs provided to fetch.");
            return CompletableFuture.completedFuture(Collections.emptyMap());
        }

        logger.info("fetchSubstagesByIds - Fetching substages with IDs: {}", substageIds);

        // Asynchronously fetch each substage based on its ID
        List<CompletableFuture<Substage>> futures = substageIds.stream()
                .map(id -> getSubstageFromSubstageId.invokeAsync(id)
                        .exceptionally(e -> {
                            logger.error("fetchSubstagesByIds - Failed to fetch Substage with ID {}: {}", id,
                                    e.getMessage());
                            return null;
                        }))
                .collect(Collectors.toList());

        // Wait for all substage fetch operations to complete
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> {
                    Map<Integer, Substage> map = new HashMap<>();
                    for (CompletableFuture<Substage> future : futures) {
                        Substage substage = future.join();
                        if (substage != null) {
                            map.put(substage.getId(), substage);
                            logger.debug("fetchSubstagesByIds - Fetched Substage: {}", substage.getId());
                        }
                    }
                    logger.info("fetchSubstagesByIds - Successfully fetched {} substages.", map.size());
                    return map;
                })
                .exceptionally(e -> {
                    logger.error("fetchSubstagesByIds - Error while fetching substages: {}", e.getMessage());
                    return Collections.emptyMap();
                });
    }

    private CompletableFuture<Map<Integer, Caster>> fetchCastersByIds(Set<Integer> casterIds) {
        if (casterIds.isEmpty()) {
            logger.info("fetchCastersByIds - No caster IDs provided to fetch.");
            return CompletableFuture.completedFuture(Collections.emptyMap());
        }

        logger.info("fetchCastersByIds - Fetching casters with IDs: {}", casterIds);

        // Asynchronously fetch each caster based on its ID
        List<CompletableFuture<Caster>> futures = casterIds.stream()
                .map(id -> getCasterFromCasterId.invokeAsync(id)
                        .exceptionally(e -> {
                            logger.error("fetchCastersByIds - Failed to fetch Caster with ID {}: {}", id,
                                    e.getMessage());
                            return null;
                        }))
                .collect(Collectors.toList());

        // Wait for all caster fetch operations to complete
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> {
                    Map<Integer, Caster> map = new HashMap<>();
                    for (CompletableFuture<Caster> future : futures) {
                        Caster caster = future.join();
                        if (caster != null) {
                            map.put(caster.getId(), caster);
                            logger.debug("fetchCastersByIds - Fetched Caster: {}", caster.getId());
                        }
                    }
                    logger.info("fetchCastersByIds - Successfully fetched {} casters.", map.size());
                    return map;
                })
                .exceptionally(e -> {
                    logger.error("fetchCastersByIds - Error while fetching casters: {}", e.getMessage());
                    return Collections.emptyMap();
                });
    }

    // eSports Helpers

    private BracketPosition convertBracketPosition(PostBracketPosition bp) {
        if (bp == null) {
            return null;
        }
        BracketPosition bracketPosition = new BracketPosition();
        bracketPosition.setPart(bp.part);
        bracketPosition.setCol(bp.col);
        bracketPosition.setOffset(bp.offset);
        return bracketPosition;
    }

    private Participant convertParticipant(PostParticipant p) {
        Participant participant = new Participant();
        participant.setSeed(p.seed);
        participant.setScore(p.score);
        participant.setForfeit(p.forfeit);
        participant.setRoster(convertToThriftRoster(p.roster));
        participant.setWinner(p.winner);
        participant.setStats(convertParticipantStats(p.stats));
        return participant;
    }

    private ParticipantStats convertParticipantStats(PostParticipantStats stats) {
        if (stats == null) {
            return null;
        }
        ParticipantStats participantStats = new ParticipantStats();
        participantStats.setKills(stats.kills);
        participantStats.setPlacement(stats.placement);
        return participantStats;
    }

    private Caster convertCaster(PostCaster c) {
        if (c == null) {
            return null;
        }

        Caster caster = new Caster();
        if (c.id != null) {
            caster.setId(c.id);
        }
        caster.setDisplayName(c.displayName);
        caster.setUsername(c.username);

        // Safely set gameId
        if (c.game != null) {
            caster.setGameId(c.game.id);
        } else {
            caster.setGameId(0); // or use an appropriate default
        }

        // Safely set deletedAt
        if (c.deletedAt != null) {
            caster.setDeletedAt(c.deletedAt.toEpochMilli());
        }
        // If deletedAt is optional and the setter accepts Long, you can omit the else
        // block

        caster.setPlatform(convertStreamingPlatform(c.platform));
        caster.setStream(convertStream(c.stream));
        caster.setRegion(convertRegion(c.region));

        return caster;
    }

    private Broadcaster convertBroadcaster(PostBroadcaster postBroadcaster) {
        if (postBroadcaster == null || postBroadcaster.broadcaster == null) {
            logger.warn("convertBroadcaster - PostBroadcaster or nested broadcaster is null.");
            return null;
        }

        Broadcaster broadcaster = new Broadcaster();

        // Extracting fields from nested broadcaster
        broadcaster.setBroadcasterId(postBroadcaster.broadcaster.id);
        broadcaster
                .setBroadcasterName(postBroadcaster.broadcaster.name != null ? postBroadcaster.broadcaster.name : "");
        broadcaster.setBroadcasterExternalId(
                postBroadcaster.broadcaster.external_id != null ? postBroadcaster.broadcaster.external_id : "");
        broadcaster.setBroadcasterPlatformId(
                postBroadcaster.broadcaster.platform != null ? postBroadcaster.broadcaster.platform.id : 0);
        broadcaster.setBroadcasterDefaultLanguageId(
                postBroadcaster.broadcaster.broadcast_defaults != null
                        && postBroadcaster.broadcaster.broadcast_defaults.language != null
                                ? postBroadcaster.broadcaster.broadcast_defaults.language.id
                                : 0);

        // Convert broadcasts
        if (postBroadcaster.broadcasts != null) {
            broadcaster.setBroadcasts(
                    postBroadcaster.broadcasts.stream()
                            .map(this::convertBroadcast)
                            .filter(Objects::nonNull) // Ensure no null broadcasts are added
                            .collect(Collectors.toList()));
        } else {
            broadcaster.setBroadcasts(new ArrayList<>());
        }

        broadcaster.setOfficial(postBroadcaster.official);

        try {
            logger.info("convertBroadcaster - Converted Broadcaster: " + objectMapper.writeValueAsString(broadcaster));
        } catch (JsonProcessingException e) {
            logger.error("JSONProcessing exception during convertBroadcaster: ", e);
        }

        return broadcaster;
    }

    private Broadcast convertBroadcast(PostBroadcast postBroadcast) {
        if (postBroadcast == null) {
            logger.warn("convertBroadcast - PostBroadcast is null.");
            return null;
        }
        Broadcast broadcast = new Broadcast();
        broadcast.setExternalId(postBroadcast.externalId != null ? postBroadcast.externalId : "");
        broadcast.setLanguageId(postBroadcast.language != null ? postBroadcast.language.id : 0);

        try {
            logger.info("convertBroadcast - Converted Broadcast: " + objectMapper.writeValueAsString(broadcast));
        } catch (JsonProcessingException e) {
            logger.error("JSONProcessing exception during convertBroadcast: ", e);
        }

        return broadcast;
    }

    private GameVersion convertGameVersion(PostGameVersion gv) {
        if (gv == null || gv.release == null) {
            return null;
        }
        GameVersion gameVersion = new GameVersion();
        gameVersion.setRelease(convertRelease(gv.release));
        return gameVersion;
    }

    private Release convertRelease(PostRelease r) {
        if (r == null) {
            return null;
        }
        Release release = new Release();
        release.setUuid(r.uuid);
        release.setDate(r.date);
        release.setDescription(r.description);
        return release;
    }

    private Coverage convertCoverage(PostCoverage c) {
        if (c == null || c.data == null) {
            return null;
        }
        PostCoverage.PostCoverageData cd = c.data;
        return new Coverage(new CoverageData(
                convertCoverageType(cd.live),
                convertCoverageType(cd.realtime),
                convertCoverageType(cd.postgame)));
    }

    private CoverageType convertCoverageType(PostCoverage.PostCoverageType ct) {
        if (ct == null) {
            return null;
        }
        CoverageType coverageType = new CoverageType();
        coverageType.setApi(convertCoverageStatus(ct.api));
        if (ct.cv != null) {
            coverageType.setCv(convertCoverageStatus(ct.cv));
        }
        if (ct.server != null) {
            coverageType.setServer(convertCoverageStatus(ct.server));
        }
        return coverageType;
    }

    private CoverageStatus convertCoverageStatus(PostCoverage.PostCoverageStatus cs) {
        if (cs == null) {
            return null;
        }
        return new CoverageStatus(cs.expectation, cs.fact);
    }

    private List<Image> convertImages(List<PostImage> postImages) {
        if (postImages == null)
            return new ArrayList<>();
        List<Image> images = new ArrayList<>();
        for (PostImage postImage : postImages) {
            Image image = new Image();
            image.setId(postImage.id);
            image.setType(postImage.type);
            image.setUrl(postImage.url);
            image.setThumbnail(postImage.thumbnail);
            image.setFallback(postImage.fallback);
            images.add(image);
        }
        return images;
    }

    private Region convertRegion(PostRegion postRegion) {
        if (postRegion == null)
            return null;
        Region region = new Region();
        region.setId(postRegion.id);
        region.setName(postRegion.name);
        region.setAbbreviation(postRegion.abbreviation);
        region.setCountry(convertCountry(postRegion.country));
        return region;
    }

    private Country convertCountry(PostCountry postCountry) {
        if (postCountry == null)
            return null;
        Country country = new Country();
        country.setId(postCountry.id);
        country.setName(postCountry.name);
        country.setAbbreviation(postCountry.abbreviation);
        country.setImages(convertImages(postCountry.images));
        return country;
    }

    private List<SocialMediaAccount> convertSocialMediaAccounts(List<PostSocialMediaAccount> postAccounts) {
        if (postAccounts == null)
            return new ArrayList<>();
        List<SocialMediaAccount> accounts = new ArrayList<>();
        for (PostSocialMediaAccount postAccount : postAccounts) {
            SocialMediaAccount account = new SocialMediaAccount();
            account.setHandle(postAccount.handle);
            account.setUrl(postAccount.url);
            account.setPlatform(convertSocialMediaPlatform(postAccount.platform));
            accounts.add(account);
        }
        return accounts;
    }

    private StreamingPlatform convertStreamingPlatform(PostStreamingPlatform postPlatform) {
        if (postPlatform == null)
            return null;
        StreamingPlatform platform = new StreamingPlatform();
        platform.setId(postPlatform.id);
        platform.setName(postPlatform.name);
        platform.setColor(postPlatform.color);
        platform.setImages(convertImages(postPlatform.images));
        return platform;
    }

    private SocialMediaPlatform convertSocialMediaPlatform(PostSocialMediaPlatform postPlatform) {
        if (postPlatform == null)
            return null;
        SocialMediaPlatform platform = new SocialMediaPlatform();
        platform.setId(postPlatform.id);
        platform.setName(postPlatform.name);
        platform.setSlug(postPlatform.slug);
        return platform;
    }

    private StandingRoster convertStandingRoster(PostStandingRoster postStandingRoster) {
        if (postStandingRoster == null)
            return null;
        StandingRoster standingRoster = new StandingRoster();
        standingRoster.setId(postStandingRoster.id);
        standingRoster.setFrom(postStandingRoster.from != null ? postStandingRoster.from.toEpochMilli() : 0);
        standingRoster.setTo(postStandingRoster.to != null ? postStandingRoster.to.toEpochMilli() : 0);
        standingRoster.setRosterId(postStandingRoster.roster != null ? postStandingRoster.roster.id : 0);
        standingRoster
                .setDeletedAt(postStandingRoster.deletedAt != null ? postStandingRoster.deletedAt.toEpochMilli() : 0);
        return standingRoster;
    }

    private Age convertAge(PostAge postAge) {
        if (postAge == null)
            return null;
        Age age = new Age();
        age.setPrecision(postAge.precision);
        age.setYears(postAge.years);
        return age;
    }

    private List<Integer> convertTeamIds(List<PostTeamInfo> postTeams) {
        if (postTeams == null)
            return new ArrayList<>();
        List<Integer> teamIds = new ArrayList<>();
        for (PostTeamInfo postTeam : postTeams) {
            teamIds.add(postTeam.id);
        }
        return teamIds;
    }

    private LolTeams convertToThriftLolTeams(PostLolTeams postTeams, int matchId) {
        LolTeams teams = new LolTeams();
        teams.setHome(convertToThriftLolTeamSummary(postTeams.home, matchId));
        teams.setAway(convertToThriftLolTeamSummary(postTeams.away, matchId));
        return teams;
    }

    private LolTeamSummary convertToThriftLolTeamSummary(PostLolTeamSummary postTeam, int matchId) {
        LolTeamSummary team = new LolTeamSummary();
        team.setRoster(convertToThriftLolRoster(postTeam.roster));
        team.setScore(postTeam.score);
        team.setIsWinner(postTeam.is_winner);
        team.setGoldEarned(postTeam.gold_earned);
        team.setTurretsDestroyed(postTeam.turrets_destroyed);
        team.setInhibitorsDestroyed(postTeam.inhibitors_destroyed);
        team.setFaction(convertToThriftLolFaction(postTeam.faction));
        team.setStructures(convertToThriftLolStructures(postTeam.structures));
        team.setCreeps(convertToThriftLolCreeps(postTeam.creeps));
        team.setPlayers(postTeam.players.stream()
                .map(this::convertToThriftLolPlayerSummary)
                .collect(Collectors.toList()));
        team.setMatchId(matchId);
        return team;
    }

    private LolRoster convertToThriftLolRoster(PostLolRoster postRoster) {
        LolRoster roster = new LolRoster();
        roster.setId(postRoster.id);
        return roster;
    }

    private LolFaction convertToThriftLolFaction(PostLolFaction postFaction) {
        LolFaction faction = new LolFaction();
        faction.setId(postFaction.id);
        return faction;
    }

    private LolStructures convertToThriftLolStructures(PostLolStructures postStructures) {
        LolStructures structures = new LolStructures();
        structures.setTurrets(convertToThriftLolTurrets(postStructures.turrets));
        structures.setInhibitors(convertToThriftLolInhibitors(postStructures.inhibitors));
        return structures;
    }

    private LolTurrets convertToThriftLolTurrets(PostLolTurrets postTurrets) {
        LolTurrets turrets = new LolTurrets();
        turrets.setTopOuter(convertToThriftLolTurret(postTurrets.top_outer));
        turrets.setTopInner(convertToThriftLolTurret(postTurrets.top_inner));
        turrets.setTopInhibitor(convertToThriftLolTurret(postTurrets.top_inhibitor));
        turrets.setTopNexus(convertToThriftLolTurret(postTurrets.top_nexus));
        turrets.setMidOuter(convertToThriftLolTurret(postTurrets.mid_outer));
        turrets.setMidInner(convertToThriftLolTurret(postTurrets.mid_inner));
        turrets.setMidInhibitor(convertToThriftLolTurret(postTurrets.mid_inhibitor));
        turrets.setBotOuter(convertToThriftLolTurret(postTurrets.bot_outer));
        turrets.setBotInner(convertToThriftLolTurret(postTurrets.bot_inner));
        turrets.setBotInhibitor(convertToThriftLolTurret(postTurrets.bot_inhibitor));
        turrets.setBotNexus(convertToThriftLolTurret(postTurrets.bot_nexus));
        return turrets;
    }

    private LolTurret convertToThriftLolTurret(PostLolTurret postTurret) {
        LolTurret turret = new LolTurret();
        turret.setStanding(postTurret.standing);
        return turret;
    }

    private LolInhibitors convertToThriftLolInhibitors(PostLolInhibitors postInhibitors) {
        LolInhibitors inhibitors = new LolInhibitors();
        inhibitors.setTop(convertToThriftLolInhibitor(postInhibitors.top));
        inhibitors.setMid(convertToThriftLolInhibitor(postInhibitors.mid));
        inhibitors.setBot(convertToThriftLolInhibitor(postInhibitors.bot));
        return inhibitors;
    }

    private LolInhibitor convertToThriftLolInhibitor(PostLolInhibitor postInhibitor) {
        LolInhibitor inhibitor = new LolInhibitor();
        inhibitor.setStanding(postInhibitor.standing);
        if (postInhibitor.respawn_time != null) {
            inhibitor.setRespawnTime(convertToThriftLolMatchClock(postInhibitor.respawn_time));
        }
        return inhibitor;
    }

    private LolCreeps convertToThriftLolCreeps(PostLolCreeps postCreeps) {
        LolCreeps creeps = new LolCreeps();
        creeps.setOverall(convertToThriftLolOverallCreeps(postCreeps.overall));
        creeps.setNeutrals(convertToThriftLolNeutralCreeps(postCreeps.neutrals));
        return creeps;
    }

    private LolOverallCreeps convertToThriftLolOverallCreeps(PostLolOverallCreeps postOverallCreeps) {
        LolOverallCreeps overallCreeps = new LolOverallCreeps();
        overallCreeps.setKills(convertToThriftLolCreepKills(postOverallCreeps.kills));
        return overallCreeps;
    }

    private LolCreepKills convertToThriftLolCreepKills(PostLolCreepKills postCreepKills) {
        LolCreepKills creepKills = new LolCreepKills();
        creepKills.setTotal(postCreepKills.total);
        return creepKills;
    }

    private LolNeutralCreeps convertToThriftLolNeutralCreeps(PostLolNeutralCreeps postNeutralCreeps) {
        LolNeutralCreeps neutralCreeps = new LolNeutralCreeps();
        neutralCreeps.setKills(convertToThriftLolNeutralCreepKills(postNeutralCreeps.kills));
        return neutralCreeps;
    }

    private LolNeutralCreepKills convertToThriftLolNeutralCreepKills(PostLolNeutralCreepKills postNeutralCreepKills) {
        LolNeutralCreepKills neutralCreepKills = new LolNeutralCreepKills();
        if (postNeutralCreepKills != null && postNeutralCreepKills.per_elite_type != null) {
            neutralCreepKills.setPerEliteType(postNeutralCreepKills.per_elite_type.stream()
                    .map(this::convertToThriftLolEliteCreepKills)
                    .collect(Collectors.toList()));
        } else {
            neutralCreepKills.setPerEliteType(new ArrayList<>()); // Set an empty list if null
        }
        return neutralCreepKills;
    }

    private LolEliteCreepKills convertToThriftLolEliteCreepKills(PostLolEliteCreepKills postEliteCreepKills) {
        LolEliteCreepKills eliteCreepKills = new LolEliteCreepKills();
        eliteCreepKills.setElite(convertToThriftLolElite(postEliteCreepKills.elite));
        eliteCreepKills.setTotal(postEliteCreepKills.total);
        return eliteCreepKills;
    }

    private LolElite convertToThriftLolElite(PostLolElite postElite) {
        LolElite elite = new LolElite();
        elite.setId(postElite.id);
        return elite;
    }

    private LolPlayerSummary convertToThriftLolPlayerSummary(PostLolPlayerSummary postPlayer) {
        LolPlayerSummary player = new LolPlayerSummary();
        player.setId(postPlayer.id);
        player.setUiIndex(postPlayer.ui_index);
        LolChampion champion = convertToThriftLolChampion(postPlayer.champion);
        if (champion != null) {
            player.setChampion(champion);
        }
        player.setKills(convertToThriftLolKills(postPlayer.kills));
        player.setAssists(convertToThriftLolAssists(postPlayer.assists));
        player.setDeaths(convertToThriftLolDeaths(postPlayer.deaths));
        player.setRevives(convertToThriftLolRevives(postPlayer.revives));
        if (postPlayer.multi_kills != null) {
            player.setMultiKills(postPlayer.multi_kills.stream()
                    .map(this::convertToThriftLolMultiKill)
                    .collect(Collectors.toList()));
        }
        player.setKillStreaks(postPlayer.kill_streaks);
        player.setItems(convertToThriftLolItems(postPlayer.items));
        if (postPlayer.summoner_spells != null) {
            player.setSummonerSpells(postPlayer.summoner_spells.stream()
                    .map(this::convertToThriftLolSummonerSpell)
                    .collect(Collectors.toList()));
        }
        player.setCreeps(convertToThriftLolCreeps(postPlayer.creeps));
        if (postPlayer.keystone != null) {
            player.setKeystone(convertToThriftLolKeystone(postPlayer.keystone));
        }
        if (postPlayer.position != null) {
            player.setPosition(convertToThriftLolPosition(postPlayer.position));
        }
        return player;
    }

    private LolChampion convertToThriftLolChampion(PostLolChampion postChampion) {
        if (postChampion == null || postChampion.id == null) {
            return null; // Return null if postChampion or its id is null
        }
        LolChampion champion = new LolChampion();
        champion.setId(postChampion.id);
        return champion;
    }

    private LolKills convertToThriftLolKills(PostLolKills postKills) {
        LolKills kills = new LolKills();
        kills.setTotal(postKills.total);
        kills.setSpecial(convertToThriftLolSpecialKills(postKills.special));
        return kills;
    }

    private LolSpecialKills convertToThriftLolSpecialKills(PostLolSpecialKills postSpecialKills) {
        LolSpecialKills specialKills = new LolSpecialKills();
        specialKills.setFirstBlood(postSpecialKills.first_blood);
        return specialKills;
    }

    private LolAssists convertToThriftLolAssists(PostLolAssists postAssists) {
        LolAssists assists = new LolAssists();
        assists.setTotal(postAssists.total);
        return assists;
    }

    private LolDeaths convertToThriftLolDeaths(PostLolDeaths postDeaths) {
        LolDeaths deaths = new LolDeaths();
        deaths.setTotal(postDeaths.total);
        return deaths;
    }

    private LolRevives convertToThriftLolRevives(PostLolRevives postRevives) {
        LolRevives revives = new LolRevives();
        revives.setFriendly(convertToThriftLolFriendlyRevives(postRevives.friendly));
        return revives;
    }

    private LolFriendlyRevives convertToThriftLolFriendlyRevives(PostLolFriendlyRevives postFriendlyRevives) {
        LolFriendlyRevives friendlyRevives = new LolFriendlyRevives();
        friendlyRevives.setGiven(convertToThriftLolReviveCount(postFriendlyRevives.given));
        friendlyRevives.setTaken(convertToThriftLolReviveCount(postFriendlyRevives.taken));
        return friendlyRevives;
    }

    private LolReviveCount convertToThriftLolReviveCount(PostLolReviveCount postReviveCount) {
        LolReviveCount reviveCount = new LolReviveCount();
        reviveCount.setTotal(postReviveCount.total);
        return reviveCount;
    }

    private LolMultiKill convertToThriftLolMultiKill(PostLolMultiKill postMultiKill) {
        LolMultiKill multiKill = new LolMultiKill();
        multiKill.setNrKills(postMultiKill.nr_kills);
        multiKill.setCount(postMultiKill.count);
        return multiKill;
    }

    private LolItems convertToThriftLolItems(PostLolItems postItems) {
        LolItems items = new LolItems();
        if (postItems.inventory != null) {
            items.setInventory(postItems.inventory.stream()
                    .map(this::convertToThriftLolItem)
                    .collect(Collectors.toList()));
        }
        if (postItems.trinket_slot != null) {
            items.setTrinketSlot(postItems.trinket_slot.stream()
                    .map(this::convertToThriftLolItem)
                    .collect(Collectors.toList()));
        }
        return items;
    }

    private LolItem convertToThriftLolItem(PostLolItem postItem) {
        LolItem item = new LolItem();
        item.setId(postItem.id);
        item.setSlot(postItem.slot);
        return item;
    }

    private LolSummonerSpell convertToThriftLolSummonerSpell(PostLolSummonerSpell postSpell) {
        LolSummonerSpell spell = new LolSummonerSpell();
        spell.setId(postSpell.id);
        spell.setSlot(postSpell.slot);
        return spell;
    }

    private LolKeystone convertToThriftLolKeystone(PostLolKeystone postKeystone) {
        LolKeystone keystone = new LolKeystone();
        keystone.setId(postKeystone.id);
        return keystone;
    }

    private LolPosition convertToThriftLolPosition(PostLolPosition postPosition) {
        LolPosition position = new LolPosition();
        position.setX(postPosition.x);
        position.setY(postPosition.y);
        // Note: normalized_coordinate and in_game_coordinate are not handled here
        // You may need to add these if they're part of your Thrift structure
        return position;
    }

    private LolPits convertToThriftLolPits(PostLolPits postPits) {
        LolPits pits = new LolPits();
        pits.setDragonPit(convertToThriftLolPit(postPits.dragon_pit));
        pits.setBaronPit(convertToThriftLolPit(postPits.baron_pit));
        return pits;
    }

    private LolPit convertToThriftLolPit(PostLolPit postPit) {
        LolPit pit = new LolPit();
        pit.setNpc(convertToThriftLolNpc(postPit.npc));
        pit.setNpcStatus(postPit.npc_status);
        if (postPit.empty_since_time != null) {
            pit.setEmptySinceTime(convertToThriftLolMatchClock(postPit.empty_since_time));
        }
        if (postPit.spawn_time != null) {
            pit.setSpawnTime(convertToThriftLolMatchClock(postPit.spawn_time));
        }
        return pit;
    }

    private LolNpc convertToThriftLolNpc(PostLolNpc postNpc) {
        if (postNpc == null) {
            return null;
        }

        LolNpc npc = new LolNpc();
        npc.setId(postNpc.id);
        return npc;
    }

    private LolMatch convertToThriftLolMatch(PostLolMatch postMatch) {
        LolMatch match = new LolMatch();
        match.setId(postMatch.id);
        match.setPatch(postMatch.patch);
        match.setPhase(postMatch.phase);
        match.setClock(convertToThriftLolMatchClock(postMatch.clock));
        match.setTimeline(convertToThriftLolMatchTimeline(postMatch.timeline));
        return match;
    }

    private LolMatchClock convertToThriftLolMatchClock(PostLolMatchClock postClock) {
        LolMatchClock clock = new LolMatchClock();
        clock.setMilliseconds(postClock.milliseconds);
        return clock;
    }

    private LolMatchTimeline convertToThriftLolMatchTimeline(PostLolMatchTimeline postTimeline) {
        LolMatchTimeline timeline = new LolMatchTimeline();
        timeline.setPhase(postTimeline.phase);
        timeline.setStart(postTimeline.start.toString());
        if (postTimeline.end != null) {
            timeline.setEnd(postTimeline.end.toString());
        } else {
            timeline.setEnd(null);
        }
        timeline.setClock(convertToThriftLolMatchClock(postTimeline.clock));
        return timeline;
    }

    private Game convertToThriftGame(PostGame postGame) {
        if (postGame == null)
            return null;
        Game game = new Game();
        game.setId(postGame.id);
        game.setName(postGame.name);
        game.setShortName(postGame.shortName);
        game.setSlug(postGame.slug);
        return game;
    }

    private TournamentCopy convertTournamentCopy(PostCopy postCopy) {
        if (postCopy == null) {
            return null;
        }
        TournamentCopy copy = new TournamentCopy();
        copy.setGeneralDescription(postCopy.generalDescription);
        copy.setShortDescription(postCopy.shortDescription);
        copy.setFormatDescription(postCopy.formatDescription);
        return copy;
    }

    private TournamentLinks convertTournamentLinks(PostLinks postLinks) {
        if (postLinks == null) {
            return null;
        }
        TournamentLinks links = new TournamentLinks();
        links.setWebsite(postLinks.website);
        links.setWiki(postLinks.wiki);
        return links;
    }

    private StringPrizePool convertStringPrizePool(PostStringPrizePool postPrizePool) {
        if (postPrizePool == null) {
            return null;
        }
        StringPrizePool prizePool = new StringPrizePool();
        prizePool.setTotal(postPrizePool.total);
        prizePool.setFirst(postPrizePool.first);
        prizePool.setSecond(postPrizePool.second);
        prizePool.setThird(postPrizePool.third);
        return prizePool;
    }

    private TournamentLocation convertTournamentLocation(PostLocation postLocation) {
        if (postLocation == null) {
            return null;
        }
        TournamentLocation location = new TournamentLocation();
        location.setHost(convertHost(postLocation.host));
        location.setParticipants(convertParticipants(postLocation.participants));
        return location;
    }

    private Host convertHost(PostHost postHost) {
        if (postHost == null) {
            return null;
        }
        Host host = new Host();
        host.setId(postHost.id);
        host.setName(postHost.name);
        host.setAbbreviation(postHost.abbreviation);
        host.setCountry(convertCountry(postHost.country));
        return host;
    }

    private List<Integer> convertStageIds(List<PostStage> postStages) {
        if (postStages == null) {
            return null;
        }
        return postStages.stream()
                .map(stage -> stage.id)
                .collect(Collectors.toList());
    }

    private List<Caster> convertTournamentCasters(List<PostCaster> postCasters) {
        if (postCasters == null) {
            return null;
        }
        return postCasters.stream()
                .map(this::convertCaster)
                .collect(Collectors.toList());
    }

    private List<Broadcaster> convertTournamentBroadcasters(List<PostBroadcaster> postBroadcasters) {
        if (postBroadcasters == null) {
            return null;
        }
        return postBroadcasters.stream()
                .map(this::convertBroadcaster)
                .collect(Collectors.toList());
    }

    private TournamentDefaults convertTournamentDefaults(PostTournamentDefaults postDefaults) {
        if (postDefaults == null) {
            return null;
        }
        TournamentDefaults defaults = new TournamentDefaults();
        defaults.setGameVersion(convertGameVersion(postDefaults.gameVersion));
        return defaults;
    }

    private List<Participant> convertParticipants(List<PostParticipant> postParticipants) {
        if (postParticipants == null) {
            return null;
        }
        return postParticipants.stream()
                .map(this::convertParticipant)
                .collect(Collectors.toList());
    }

    private List<Standing> convertStandings(List<PostStanding> postStandings) {
        if (postStandings == null) {
            return null;
        }
        return postStandings.stream()
                .map(this::convertStanding)
                .collect(Collectors.toList());
    }

    private Standing convertStanding(PostStanding postStanding) {
        if (postStanding == null) {
            return null;
        }
        Standing standing = new Standing();
        standing.setRosterId(postStanding.roster.id);
        standing.setPoints(postStanding.points);
        standing.setWins(postStanding.wins);
        standing.setDraws(postStanding.draws);
        standing.setLosses(postStanding.losses);
        standing.setMatchDiff(postStanding.matchDiff);
        standing.setScoreDiff(postStanding.scoreDiff);
        return standing;
    }

    private SubstageRules convertSubstageRules(PostRules postRules) {
        if (postRules == null) {
            return null;
        }
        SubstageRules rules = new SubstageRules();
        rules.setAdvance(convertAdvanceRule(postRules.advance));
        rules.setDescend(convertDescendRule(postRules.descend));
        rules.setPoints(convertPointsRule(postRules.points));
        return rules;
    }

    private AdvanceRule convertAdvanceRule(PostAdvance postAdvance) {
        if (postAdvance == null) {
            return null;
        }
        AdvanceRule advance = new AdvanceRule();
        advance.setNumber(postAdvance.number);
        if (postAdvance.substage != null) {
            advance.setSubstageId(postAdvance.substage.id);
        }
        return advance;
    }

    private DescendRule convertDescendRule(PostDescend postDescend) {
        if (postDescend == null) {
            return null;
        }
        DescendRule descend = new DescendRule();
        descend.setNumber(postDescend.number);
        if (postDescend.substage != null) {
            descend.setSubstageId(postDescend.substage.id);
        }
        return descend;
    }

    private PointsRule convertPointsRule(PostPoints postPoints) {
        if (postPoints == null) {
            return null;
        }
        PointsRule points = new PointsRule();
        points.setWin(postPoints.win);
        points.setDraw(postPoints.draw);
        points.setLoss(postPoints.loss);
        points.setScope(postPoints.scope);
        return points;
    }

    private SubstageDefaults convertSubstageDefaults(PostSubstageDefaults postDefaults) {
        if (postDefaults == null) {
            return null;
        }
        SubstageDefaults defaults = new SubstageDefaults();
        defaults.setGameVersion(convertGameVersion(postDefaults.gameVersion));
        defaults.setSeriesFormat(convertFormat(postDefaults.seriesFormat));
        return defaults;
    }

    private SubstageFormat convertSubstageFormat(PostSubstageFormat postFormat) {
        if (postFormat == null) {
            return null;
        }
        SubstageFormat format = new SubstageFormat();
        format.setPoints(convertPointsRule(postFormat.points));
        format.setMovements(convertMovements(postFormat.movements));
        return format;
    }

    private List<Movement> convertMovements(List<PostMovement> postMovements) {
        if (postMovements == null) {
            return null;
        }
        return postMovements.stream()
                .map(this::convertMovement)
                .collect(Collectors.toList());
    }

    private Movement convertMovement(PostMovement postMovement) {
        if (postMovement == null) {
            return null;
        }
        Movement movement = new Movement();
        movement.setPosition(postMovement.position);
        movement.setSubstageId(postMovement.substage.id);
        movement.setType(postMovement.type);
        return movement;
    }

    // Using our stream instead of java.util.stream
    private com.apollo.backend.data.Stream convertStream(PostStream postStream) {
        if (postStream == null) {
            return null;
        }
        com.apollo.backend.data.Stream stream = new com.apollo.backend.data.Stream();
        stream.setId(postStream.id);
        stream.setUsername(postStream.username);
        stream.setDisplayName(postStream.displayName);
        stream.setStatusText(postStream.statusText);
        stream.setViewerCount(postStream.viewerCount);
        stream.setOnline(postStream.online);
        stream.setLastOnline(postStream.lastOnline != null ? postStream.lastOnline.toEpochMilli() : 0);
        stream.setImages(convertImages(postStream.images));
        stream.setPlatform(convertStreamingPlatform(postStream.platform));
        return stream;
    }

    // Fetching summary helper - TOOD: Maybe need to change
    private boolean isCoverageAvailable(Match match) {
        // Check if the match is over, as this is when coverage should be available
        if (!"over".equals(match.getLifecycle())) {
            return false;
        }

        Coverage coverage = match.getCoverage();
        if (coverage != null && coverage.getData() != null && coverage.getData().getLive() != null) {
            CoverageType liveCoverage = coverage.getData().getLive();
            CoverageStatus cvStatus = liveCoverage.getCv();
            if (cvStatus != null) {
                return "available".equals(cvStatus.getExpectation()) && "available".equals(cvStatus.getFact());
            }
        }
        return false;
    }

    private void collectAssetIds(PostLolMatchSummary summary, Set<Integer> assetIds) {
        if (summary == null || summary.teams == null) {
            return;
        }

        for (PostLolTeamSummary team : Arrays.asList(summary.teams.home, summary.teams.away)) {
            if (team == null || team.players == null) {
                continue;
            }

            for (PostLolPlayerSummary player : team.players) {
                if (player == null) {
                    continue;
                }

                if (player.champion != null) {
                    assetIds.add(player.champion.id);
                }

                if (player.keystone != null) {
                    assetIds.add(player.keystone.id);
                }

                if (player.items != null) {
                    if (player.items.inventory != null) {
                        for (PostLolItem item : player.items.inventory) {
                            if (item != null) {
                                assetIds.add(item.id);
                            }
                        }
                    }

                    if (player.items.trinket_slot != null) {
                        for (PostLolItem item : player.items.trinket_slot) {
                            if (item != null) {
                                assetIds.add(item.id);
                            }
                        }
                    }
                }

                if (player.summoner_spells != null) {
                    for (PostLolSummonerSpell spell : player.summoner_spells) {
                        if (spell != null) {
                            assetIds.add(spell.id);
                        }
                    }
                }
            }

            if (team.creeps != null && team.creeps.neutrals != null &&
                    team.creeps.neutrals.kills != null && team.creeps.neutrals.kills.per_elite_type != null) {
                for (PostLolEliteCreepKills eliteKills : team.creeps.neutrals.kills.per_elite_type) {
                    if (eliteKills != null && eliteKills.elite != null) {
                        assetIds.add(eliteKills.elite.id);
                    }
                }
            }
        }

        if (summary.pits != null) {
            if (summary.pits.dragon_pit != null && summary.pits.dragon_pit.npc != null) {
                assetIds.add(summary.pits.dragon_pit.npc.id);
            }
            if (summary.pits.baron_pit != null && summary.pits.baron_pit.npc != null) {
                assetIds.add(summary.pits.baron_pit.npc.id);
            }
        }
    }

    private Set<Integer> collectPlayerAssetIds(LolPlayerSummary player) {
        Set<Integer> assetIds = new HashSet<>();
        if (player == null) {
            return assetIds;
        }

        // Champion
        if (player.getChampion() != null) {
            assetIds.add(player.getChampion().getId());
        }
        // Keystone
        if (player.getKeystone() != null) {
            assetIds.add(player.getKeystone().getId());
        }
        // Items
        if (player.getItems() != null) {
            if (player.getItems().getInventory() != null) {
                player.getItems().getInventory().stream()
                        .filter(Objects::nonNull)
                        .forEach(item -> assetIds.add(item.getId()));
            }
            if (player.getItems().getTrinketSlot() != null) {
                player.getItems().getTrinketSlot().stream()
                        .filter(Objects::nonNull)
                        .forEach(item -> assetIds.add(item.getId()));
            }
        }
        // Summoner spells
        if (player.getSummonerSpells() != null) {
            player.getSummonerSpells().stream()
                    .filter(Objects::nonNull)
                    .forEach(spell -> assetIds.add(spell.getId()));
        }
        return assetIds;
    }

    private Set<Integer> collectTeamAssetIds(LolTeamSummary team) {
        Set<Integer> assetIds = new HashSet<>();
        if (team == null) {
            return assetIds;
        }

        // Collect from all players in the team
        if (team.getPlayers() != null) {
            team.getPlayers().stream()
                    .filter(Objects::nonNull)
                    .map(this::collectPlayerAssetIds)
                    .forEach(assetIds::addAll);
        }

        // Collect from neutral creeps if present
        if (team.getCreeps() != null &&
                team.getCreeps().getNeutrals() != null &&
                team.getCreeps().getNeutrals().getKills() != null &&
                team.getCreeps().getNeutrals().getKills().getPerEliteType() != null) {

            team.getCreeps().getNeutrals().getKills().getPerEliteType().stream()
                    .filter(eliteKills -> eliteKills != null && eliteKills.getElite() != null)
                    .forEach(eliteKills -> assetIds.add(eliteKills.getElite().getId()));
        }
        return assetIds;
    }

    public class ApiException extends Exception {
        public ApiException(String message) {
            super(message);
        }
    }

    private void waitIfNecessary() throws InterruptedException {
        while (true) {
            int remaining = remainingRequests.get();
            long reset = resetTime.get();
            long now = Instant.now().toEpochMilli();

            if (remaining > 0 || now >= reset) {
                break;
            }

            long sleepTime = Math.max(0, reset - now);
            System.out.println("Rate limit reached. Waiting for " + sleepTime + "ms");
            Thread.sleep(sleepTime);
        }
    }

    private void updateRateLimitInfo(Map<String, String> headers) {
        headers.forEach((key, value) -> {
            if (value != null) {
                switch (key.toLowerCase()) {
                    case "x-ratelimit-remaining":
                        remainingRequests.set(Integer.parseInt(value));
                        break;
                    case "x-ratelimit-reset":
                        resetTime.set(Instant.now().toEpochMilli() + Long.parseLong(value));
                        break;
                    case "x-ratelimit-limit":
                        rateLimit.set(Integer.parseInt(value));
                        break;
                    case "x-ratelimit-burst":
                        burstLimit.set(Integer.parseInt(value));
                        break;
                }
            }
        });
    }

    private void handleException(Exception e, String context, int identifier) {
        System.err.println("Error fetching " + context + " " + identifier + ": " + e.getMessage());
        if (e instanceof IOException) {
            IOException ioException = (IOException) e;
            if (ioException.getMessage().contains("429")) { // Assuming 429 is mentioned in the error message
                System.out.println("Rate limit hit. Retrying after 1000ms");
                try {
                    Thread.sleep(1000); // Wait for 1 second before retrying
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            } else {
                throw new RuntimeException(e);
            }
        } else {
            throw new RuntimeException(e);
        }
    }

    // Spaces

    public CompletableFuture<ItemStats> getSpaceStats(String space) {
        if (!ApolloSpaces.SPACE_MAP.containsKey(space)) {
            return CompletableFuture.completedFuture(null);
        }
        return batchSpaceStats.invokeAsync(Arrays.asList(space)).thenApply(info -> info.get(space));
    }

    public CompletableFuture<Boolean> postFollowSpace(long accountId, String space) {
        if (!ApolloSpaces.SPACE_MAP.containsKey(space)) {
            return CompletableFuture.completedFuture(false);
        }
        return followSpaceDepot.appendAsync(new FollowSpace(accountId, space, System.currentTimeMillis()))
                .thenApply(res -> true);
    }

    public CompletableFuture<Boolean> postRemoveFollowSpace(long accountId, String space) {
        if (!ApolloSpaces.SPACE_MAP.containsKey(space)) {
            return CompletableFuture.completedFuture(false);
        }
        return followSpaceDepot.appendAsync(new RemoveFollowSpace(accountId, space, System.currentTimeMillis()))
                .thenApply(res -> true);
    }

    public CompletableFuture<Boolean> isFollowingSpace(long accountId, String space) {
        if (!ApolloSpaces.SPACE_MAP.containsKey(space)) {
            return CompletableFuture.completedFuture(false);
        }
        return spaceToFollowers.selectOneAsync(Path.key(space).view(Ops.CONTAINS, accountId));
    }

    // TODO: Idk if this works
    public CompletableFuture<List<String>> getFollowedSpaces(long accountId) {
        return spaceToFollowers.selectAsync(Path.key(accountId).all())
                .thenApply(entries -> entries.stream()
                        .map(Object::toString)
                        .filter(ApolloSpaces.SPACE_MAP::containsKey)
                        .collect(Collectors.toList()));
    }

    public CompletableFuture<Map<String, ItemStats>> getTrendingSpaces(Integer limitMaybe, Integer offsetMaybe) {
        long offset = offsetMaybe == null ? 0 : offsetMaybe;
        int defaultLimit = 10;
        int maxLimit = 20;
        int limit = Math.min(limitMaybe == null ? defaultLimit : limitMaybe, maxLimit);
        return spaceTrends.selectAsync(Path.all().first())
                .thenApply(spaces -> spaces.stream()
                        .filter(ApolloSpaces.SPACE_MAP::containsKey)
                        .skip(offset)
                        .limit(limit)
                        .collect(Collectors.toList()))
                .thenCompose(batchSpaceStats::invokeAsync)
                .thenApply(res -> res == null ? new HashMap<>() : res);
    }

    public CompletableFuture<StatusQueryResults> getSpaceTimeline(String space, Long requestAccountIdMaybe,
            StatusPointer offsetMaybe, Integer limitMaybe) {
        if (!ApolloSpaces.SPACE_MAP.containsKey(space)) {
            return CompletableFuture
                    .completedFuture(new StatusQueryResults(new ArrayList<>(), new HashMap<>(), false, false));
        }
        return queryStatusesWithPaging(
                (offset, limit) -> spaceToStatusPointersReverse
                        .selectOneAsync(Path.key(space, offset).nullToVal(-1L))
                        .thenCompose(timelineIndex -> getSpaceTimeline.invokeAsync(space, requestAccountIdMaybe,
                                timelineIndex, limit)),
                offsetMaybe, limitMaybe, MAX_PAGING_ITERATIONS);
    }

    // OAuth

    public CompletableFuture<Application> postApplication(PostApplication params) {
        Application newApp = new Application(
                ApolloHelpers.randomString(16), // client_id
                ApolloHelpers.generateSecureRandomString(32), // client_secret
                params.client_name,
                params.redirect_uris,
                params.scopes);

        return applicationDepot
                .appendAsync(newApp)
                .thenApply(v -> newApp);
    }

    public CompletableFuture<Application> getApplication(String clientId) {
        return getApplicationFromClientId.invokeAsync(clientId);
    }

    static {
        if (System.getenv("AWS_EXECUTION_ENV") != null) {
            System.out.println("Running on EC2, using default credentials provider chain");
            credentialsProvider = DefaultCredentialsProvider.create();
        } else {
            System.out.println("Local development, loading from .env file");
            String accessKeyId = dotenv.get("AWS_ACCESS_KEY_ID");
            String secretAccessKey = dotenv.get("AWS_SECRET_ACCESS_KEY");

            if (accessKeyId == null || secretAccessKey == null) {
                throw new IllegalStateException("AWS credentials not found in .env file");
            }

            credentialsProvider = StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(accessKeyId, secretAccessKey));
        }
    }

    public static AwsCredentialsProvider getAwsCredentialsProvider() {
        return credentialsProvider;
    }

    public static software.amazon.awssdk.regions.Region getAwsRegion() {
        return software.amazon.awssdk.regions.Region.US_EAST_2; // TODO: Make configurable if needed
    }

    // Hermes stuff

    // Method to start the WebSocket connection
    public void startWebSocketConnection() {
        List<String> channels = Arrays.asList(
                "series_updates",
                "match_updates",
                "player_updates",
                "team_updates",
                "lol_live_cv_states",
                "lol_live_cv_events");
        Map<String, String> filters = new HashMap<>();
        filters.put("game", "2"); // Game ID for League of Legends
        String queue = "liveDataQueue";

        apiClient.connectToWebSocket(channels, filters, queue);
    }

    public void broadcastLiveMatchSummary(GetLiveMatch getLiveMatch) {
        String stream = "live-match/" + getLiveMatch.id;
        // Broadcast to all sessions subscribed to "live-match/{matchId}"
        ApolloApiStreamingConfig.SESSION_ID_TO_STATE.forEach((sessionId, streamState) -> {
            if (stream.equals(streamState.stream)) { // Match exact stream identifier
                ApolloApiStreamingConfig.sendLiveMatch(streamState.session, streamState.sink, stream, getLiveMatch);
            }
        });
    }

    public void handleIncomingMessage(String message) {
        try {
            JsonNode rootNode = objectMapper.readTree(message);

            // Determine the channel from the message
            String channel = rootNode.get("channel").asText();

            // Process messages based on the channel
            switch (channel) {
                case "match_updates":
                    processMatchUpdate(rootNode);
                    break;
                case "series_updates":
                    processSeriesUpdate(rootNode);
                    break;
                case "lol_live_cv_states":
                    processLiveLolMatchSummary(rootNode);
                    break;
                case "lol_live_cv_events":
                    processLiveLolMatchEvent(rootNode);
                    break;
                default:
                    logger.warn("Received message for unhandled channel: {}", channel);
            }
        } catch (Exception e) {
            logger.error("Error processing incoming message", e);
        }
    }

    // Main processing method for series updates
    private void processSeriesUpdate(JsonNode rootNode) {
        try {
            JsonNode payload = rootNode.get("payload");
            if (payload == null) {
                logger.warn("Missing payload in series update message");
                return;
            }

            // Extract the series ID from the state
            JsonNode stateNode = payload.get("state");
            if (stateNode == null || !stateNode.has("id")) {
                logger.warn("Missing series ID in series update message");
                return;
            }
            int seriesId = stateNode.get("id").asInt();

            // Extract the list of patches
            JsonNode patchesNode = payload.get("patch");
            if (patchesNode == null || !patchesNode.isArray()) {
                logger.warn("No patches found in series update message");
                return;
            }

            boolean startTimeChanged = false;
            for (JsonNode patch : patchesNode) {
                String path = patch.get("path").asText();
                if (path.equals("/start")) {
                    startTimeChanged = true;
                    break;
                }
            }

            String filter = String.format("id=" + seriesId);

            if (startTimeChanged) {
                // Handle deletion of old entry from data store
                ApolloHelpers.removeOldSeries(seriesId);
            }

            // Wrap fetchAndStoreSeries in a CompletableFuture
            CompletableFuture<Void> fetchFuture = CompletableFuture.runAsync(() -> {
                fetchAndStoreSeries(filter, "", 0, 1);
            });

            // Chain the calls after the fetch is complete
            fetchFuture.thenCompose(voidResult -> {
                // Now retrieve the series from the data store
                return getSeries(seriesId);
            }).thenAccept(getSeries -> {
                if (getSeries != null) {
                    // Broadcast the updated series to clients
                    ApolloApiStreamingConfig.sendSeriesUpdate(getSeries);
                    logger.info("Series {} updated and broadcasted successfully", seriesId);
                } else {
                    logger.warn("Failed to retrieve series with ID {} from data store", seriesId);
                }
            }).exceptionally(ex -> {
                logger.error("Error processing series update for ID {}", seriesId, ex);
                return null;
            });

        } catch (Exception e) {
            logger.error("Error processing series update message", e);
        }
    }

    // Method to process match updates
    private void processMatchUpdate(JsonNode rootNode) {
        try {
            JsonNode payload = rootNode.get("payload");
            if (payload == null) {
                logger.warn("Missing payload in match update message");
                return;
            }

            // Extract the match ID from the state
            JsonNode stateNode = payload.get("state");
            if (stateNode == null || !stateNode.has("id")) {
                logger.warn("Missing match ID in match update message");
                return;
            }
            int matchId = stateNode.get("id").asInt();

            // Wrap fetchAndStoreMatch in a CompletableFuture
            CompletableFuture<Void> fetchFuture = CompletableFuture.runAsync(() -> {
                fetchAndStoreMatch(matchId);
            });

            // Chain the calls after the fetch is complete
            fetchFuture.thenCompose(voidResult -> {
                // Now retrieve the match from the data store
                return getMatch(matchId);
            }).thenAccept(getMatch -> {
                if (getMatch != null) {
                    // Broadcast the updated match to clients
                    ApolloApiStreamingConfig.sendMatchUpdate(getMatch);
                    logger.info("Match {} updated and broadcasted successfully", matchId);
                } else {
                    logger.warn("Failed to retrieve match with ID {} from data store", matchId);
                }
            }).exceptionally(ex -> {
                logger.error("Error processing match update for ID {}", matchId, ex);
                return null;
            });

        } catch (Exception e) {
            logger.error("Error processing match update message", e);
        }
    }

    // Method to process live LoL match summaries
    public void processLiveLolMatchSummary(JsonNode rootNode) {
        try {
            // Deserialize the incoming websocket message
            PostLiveLolMatchSummary liveSummary = objectMapper.treeToValue(rootNode, PostLiveLolMatchSummary.class);

            // Convert to Thrift
            LiveLolMatchSummary thriftLiveSummary = convertToThriftLiveLolMatchSummary(liveSummary);

            // Store in Rama
            liveLolMatchSummaryDepot.appendAsync(thriftLiveSummary).join();

            // Get enriched live match
            CompletableFuture<GetLiveMatch> liveMatchFuture = getLiveMatch(thriftLiveSummary.getMatchId());

            liveMatchFuture.thenAccept(getLiveMatch -> {
                if (getLiveMatch != null) {
                    broadcastLiveMatchSummary(getLiveMatch);
                    logger.info("Broadcasted live match update for matchId: {}", getLiveMatch.id);
                } else {
                    logger.warn("No live match found for matchId: {}", thriftLiveSummary.getMatchId());
                }
            }).exceptionally(e -> {
                logger.error("Error processing live match update", e);
                return null;
            });

        } catch (Exception e) {
            logger.error("Error processing live LoL match summary", e);
        }
    }

    public void processLiveLolMatchEvent(JsonNode rootNode) {
        try {
            // TODO: Implement the logic to process live LoL match events
            // For now, you can leave this method empty or add a simple log statement
            System.out.println("Received live LoL match event.");
        } catch (Exception e) {
            System.err.println("Error processing live LoL match event: " + e.getMessage());
        }
    }

    // Conversion method from PostLiveLolMatchSummary to LiveLolMatchSummary (Thrift
    // object)
    private LiveLolMatchSummary convertToThriftLiveLolMatchSummary(PostLiveLolMatchSummary liveSummary) {
        LiveLolMatchSummary thriftSummary = new LiveLolMatchSummary();
        thriftSummary.setChannel(liveSummary.channel);
        thriftSummary.setUuid(liveSummary.uuid);
        // Store the timestamp as milliseconds since epoch
        thriftSummary.setCreated(liveSummary.created.toEpochMilli());
        thriftSummary.setPayload(convertToThriftLiveLolMatchPayload(liveSummary.payload));
        thriftSummary.setMatchId(liveSummary.payload.match.id);
        return thriftSummary;
    }

    private LiveLolMatchPayload convertToThriftLiveLolMatchPayload(PostLiveLolPayload payload) {
        LiveLolMatchPayload thriftPayload = new LiveLolMatchPayload();
        thriftPayload.setIndex(payload.index);
        // Store the timestamp as milliseconds since epoch
        thriftPayload.setTimestamp(payload.timestamp.toString());
        thriftPayload.setMatch(convertToThriftLolMatch(payload.match));
        thriftPayload.setEventType(payload.eventType);
        thriftPayload.setEventData(convertToThriftLolMatchSummary(payload.eventData));
        return thriftPayload;
    }

    // Getters

    public CompletableFuture<GetLiveMatch> getLiveMatch(int matchId) {
        if (matchId == -1) {
            logger.warn("Invalid matchId: {}. Skipping live match fetch.", matchId);
            return CompletableFuture.completedFuture(null);
        }

        logger.info("getLiveMatch - Fetching live match with ID: {}", matchId);

        return getMatchFromMatchId.invokeAsync(matchId)
                .thenCompose(match -> {
                    if (match == null) {
                        logger.warn("getLiveMatch - No match found with ID: {}", matchId);
                        return CompletableFuture.completedFuture(null);
                    }

                    logger.info("getLiveMatch - Match found: {}", match.getId());

                    // Fetch participant data in parallel
                    List<CompletableFuture<GetParticipant>> participantFutures = match.getParticipants().stream()
                            .map(this::fetchFullParticipantData)
                            .collect(Collectors.toList());

                    // Fetch live match summary from Rama
                    CompletableFuture<GetLolMatchSummary> lolSummaryFuture = getLiveLolMatchSummaryFromMatchId
                            .invokeAsync(matchId)
                            .thenCompose(thriftSummary -> {
                                if (thriftSummary == null) {
                                    logger.warn("getLiveMatch - ThriftSummary is null for matchId: {}", matchId);
                                    return CompletableFuture.completedFuture(null);
                                }

                                LolMatchSummary matchSummary = thriftSummary.getPayload().getEventData();
                                LolMatch matchFromPayload = thriftSummary.getPayload().getMatch();

                                // Extract the clock
                                GetLolMatchClock clock = null;
                                if (matchFromPayload != null && matchFromPayload.isSetClock()) {
                                    clock = new GetLolMatchClock(matchFromPayload.getClock());
                                }

                                Set<Integer> assetIds = matchSummary.getAssetIds();

                                // Fetch assets
                                List<CompletableFuture<Asset>> assetFutures = assetIds.stream()
                                        .map(this::getAsset)
                                        .collect(Collectors.toList());

                                return CompletableFuture.allOf(assetFutures.toArray(new CompletableFuture[0]))
                                        .thenApply(v -> {
                                            Map<Integer, GetAsset> assetMap = assetFutures.stream()
                                                    .map(CompletableFuture::join)
                                                    .filter(Objects::nonNull)
                                                    .collect(Collectors.toMap(Asset::getId, GetAsset::new));

                                            GetLolMatchSummary getLolMatchSummary = new GetLolMatchSummary(matchSummary,
                                                    assetMap);
                                            // Set the clock if necessary
                                            getLolMatchSummary.match = new GetLolMatch(matchFromPayload);
                                            return getLolMatchSummary;
                                        });
                            })
                            .exceptionally(e -> {
                                logger.error("getLiveMatch - Error processing live match summary for matchId: {}",
                                        matchId, e);
                                return null;
                            });

                    // Combine all futures
                    CompletableFuture<?>[] allFuturesArray = Stream.concat(
                            participantFutures.stream(),
                            Stream.of(lolSummaryFuture))
                            .toArray(CompletableFuture[]::new);

                    return CompletableFuture.allOf(allFuturesArray)
                            .thenApply(v -> {
                                GetLiveMatch getLiveMatch = new GetLiveMatch(match);
                                getLiveMatch.participants = participantFutures.stream()
                                        .map(CompletableFuture::join)
                                        .collect(Collectors.toList());

                                GetLolMatchSummary lolSummary = lolSummaryFuture.join();
                                if (lolSummary != null) {
                                    // Set the clock
                                    if (lolSummary.match != null && lolSummary.match.clock != null) {
                                        getLiveMatch.clock = lolSummary.match.clock;
                                        logger.info("getLiveMatch - Integrated match.clock for matchId: {}", matchId);
                                    } else {
                                        logger.warn("getLiveMatch - Clock is null for matchId: {}", matchId);
                                    }

                                    mergeStatsIntoParticipants(getLiveMatch.participants, lolSummary);
                                    logger.info("getLiveMatch - Merged stats for matchId: {}", matchId);
                                } else {
                                    logger.warn("getLiveMatch - LolSummary is null for matchId: {}", matchId);
                                }

                                logger.info("getLiveMatch - Created GetLiveMatch for matchId: {}", matchId);
                                return getLiveMatch;
                            });
                });
    }

}
