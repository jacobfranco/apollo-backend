package com.apollo.backendapi;

import com.google.common.collect.Lists;

import clojure.lang.PersistentVector;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.time.Instant;
import java.util.AbstractMap.SimpleEntry;

import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import io.github.cdimascio.dotenv.Dotenv;

import com.apollo.backend.*;
import com.apollo.backend.data.*;
import com.apollo.backend.modules.*;
import com.apollo.backendapi.pojos.*;
import com.rpl.rama.*;
import com.rpl.rama.cluster.ClusterManagerBase;
import com.rpl.rama.ops.Ops;
import com.rpl.rama.diffs.*;

public class ApolloApiManager {

    private ObjectMapper objectMapper;

     // Load environment variables for Abios
     private static final Dotenv dotenv = Dotenv.load();
     private static final String ABIOS_SECRET = dotenv.get("ABIOS_SECRET");

    private final AbiosApiClient apiClient;
 

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

    // Relationships Depots
    private final Depot authCodeDepot;
    private final Depot followAndBlockAccountDepot;
    private final Depot muteAccountDepot;
    private final Depot featureAccountDepot;
    private final Depot filterDepot;
    private final Depot removeFollowSuggestionDepot;
    private final Depot followHashtagDepot;

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
    private final PState statusTrends;
    private final PState accountIdToHashtagActivity;
    private final PState hashtagToStatusPointers;
    private final PState hashtagToStatusPointersReverse;

    // Hashtag Queries
    private final QueryTopologyClient<Map<String, ItemStats>> batchHashtagStats;
    private final QueryTopologyClient<StatusQueryResults> getHashtagTimeline;

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

    // ESports PStates
    private final PState seriesIdToSeries;


    public ApolloApiManager(ClusterManagerBase cluster) {

        this.apiClient = new AbiosApiClient(ABIOS_SECRET);

        this.objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

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

        // Relationships Depots
        authCodeDepot = cluster.clusterDepot(RELATIONSHIPS_MODULE_NAME, "*authCodeDepot");
        followAndBlockAccountDepot = cluster.clusterDepot(RELATIONSHIPS_MODULE_NAME, "*followAndBlockAccountDepot");
        muteAccountDepot = cluster.clusterDepot(RELATIONSHIPS_MODULE_NAME, "*muteAccountDepot");
        featureAccountDepot = cluster.clusterDepot(RELATIONSHIPS_MODULE_NAME, "*featureAccountDepot");
        filterDepot = cluster.clusterDepot(RELATIONSHIPS_MODULE_NAME, "*filterDepot");
        removeFollowSuggestionDepot = cluster.clusterDepot(RELATIONSHIPS_MODULE_NAME, "*removeFollowSuggestionDepot");
        followHashtagDepot = cluster.clusterDepot(RELATIONSHIPS_MODULE_NAME, "*followHashtagDepot");

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
        hashtagToStatusPointersReverse = cluster.clusterPState(HASHTAGS_MODULE_NAME, "$$hashtagToStatusPointersReverse");

        // Hashtag Queries
        batchHashtagStats = cluster.clusterQuery(HASHTAGS_MODULE_NAME, "batchHashtagStats");
        getHashtagTimeline = cluster.clusterQuery(HASHTAGS_MODULE_NAME, "getHashtagTimeline");

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

        // ESports PStates
        seriesIdToSeries = cluster.clusterPState(ESPORTS_MODULE_NAME, "$$seriesIdToSeries");


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
                new RemoveStatus(statusPointer.authorId, statusPointer.statusId, Instant.now().toEpochMilli()));
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

    public CompletableFuture<StatusQueryResult> postLikeStatus(long favoriterId, StatusPointer pointer) {
        return likeStatusDepot.appendAsync(new LikeStatus(favoriterId, pointer, System.currentTimeMillis()))
                .thenCompose(res -> this.getStatus(favoriterId, pointer))
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

    public CompletableFuture<StatusQueryResult> postRemoveLikeStatus(long favoriterId, StatusPointer pointer) {
        return likeStatusDepot.appendAsync(new LikeStatus(favoriterId, pointer, System.currentTimeMillis()))
                .thenCompose(res -> this.getStatus(favoriterId, pointer))
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
                    // get the favoriters
                    SortedRangeFromOptions options = SortedRangeFromOptions.excludeStart().maxAmt(limit);
                    CompletableFuture<List<Long>> favoriterIdsFuture = statusIdToLikers.selectAsync(authorId,
                            Path.key(statusId).sortedMapRangeFrom(offset, options).mapKeys());
                    return favoriterIdsFuture.thenCompose(this::getAccountsFromAccountIds)
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
        return filterDepot.appendAsync(new RemoveFilter(filterId, accountId, System.currentTimeMillis()));
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

    public CompletableFuture<StatusQueryResults> getHashtagTimeline(String hashtag, Long requestAccountIdMaybe, StatusPointer offsetMaybe, Integer limitMaybe) {
        return queryStatusesWithPaging((offset, limit) ->
                  hashtagToStatusPointersReverse.selectOneAsync(Path.key(hashtag, offset).nullToVal(-1L))
                                                .thenCompose(timelineIndex -> getHashtagTimeline.invokeAsync(hashtag, requestAccountIdMaybe, timelineIndex, limit)),
                offsetMaybe, limitMaybe, MAX_PAGING_ITERATIONS);
    }

    public CompletableFuture<QueryResults<StatusQueryResult, Map>> getStatusSearch(long requestAccountId, Long authorIdMaybe, List<String> terms, Map startParamsMaybe, Integer limitMaybe) {
        int defaultLimit = 40;
        int maxLimit = 80;
        return queryWithPaging(
            (offset, limit) -> {
                CompletableFuture<Map> matchListFuture = statusTermsSearch.invokeAsync(authorIdMaybe != null ? authorIdMaybe : requestAccountId, terms, offset, limit);
                return matchListFuture.thenCompose(result -> {
                    Map nextParams = ApolloApiHelpers.createSearchParams(result);
                    List<StatusPointer> matchList = ((List<List>) result.get("matchList")).stream().map(pair -> new StatusPointer((Long) pair.get(0), (Long) pair.get(1))).collect(Collectors.toList());
                    return getStatusesFromPointers.invokeAsync(requestAccountId, matchList, new QueryFilterOptions(FilterContext.Public, false))
                                                  .thenApply(statusQueryResults -> {
                                                      List<StatusQueryResult> filtered = new ArrayList<>();
                                                      for (StatusResultWithId sqr : statusQueryResults.results) {
                                                          // if authorIdMaybe is set, we are searching for only a particular user's statuses.
                                                          // in that case, only include results written by that user (i.e. filter out mentions)
                                                          if (authorIdMaybe == null || sqr.status.author.accountId == authorIdMaybe) filtered.add(new StatusQueryResult(sqr, statusQueryResults.mentions));
                                                      }
                                                      return new QueryResults<>(filtered, nextParams == null, nextParams, ApolloApiHelpers.createLinkHeaderParams(nextParams));
                                                  });
                });
            },
            startParamsMaybe,
            Math.min(limitMaybe == null ? defaultLimit : limitMaybe, maxLimit),
            MAX_PAGING_ITERATIONS);
    }

    public CompletableFuture<QueryResults<SimpleEntry<String, ItemStats>, Map>> getHashtagSearch(String term, Map startParamsMaybe, Integer limitMaybe) {
        int defaultLimit = 40;
        int maxLimit = 80;
        int limit = Math.min(limitMaybe == null ? defaultLimit : limitMaybe, maxLimit);
        CompletableFuture<Map> matchListFuture = hashtagSearch.invokeAsync(term, startParamsMaybe, limit);
        return matchListFuture.thenCompose(result -> {
            Map nextParams = ApolloApiHelpers.createSearchParams(result);
            List<String> matchList = (List<String>) result.get("matchList");
            return batchHashtagStats.invokeAsync(matchList.stream().distinct().collect(Collectors.toList()))
                                    .thenApply(hashtagToStats -> {
                                        if (hashtagToStats == null) hashtagToStats = new HashMap<>();
                                        List<SimpleEntry<String, ItemStats>> results = new ArrayList<>();
                                        for (Map.Entry<String, ItemStats> entry : hashtagToStats.entrySet()) {
                                            results.add(new SimpleEntry<>(entry.getKey(), entry.getValue()));
                                        }
                                        return new QueryResults<>(results, nextParams == null, nextParams, ApolloApiHelpers.createLinkHeaderParams(nextParams));
                                    });
        });
    }

    public CompletableFuture<Conversation> getConversationFromStatusId(long accountId, StatusPointer pointer) {
        return statusIdToConvoId.selectOneAsync(pointer.authorId, Path.key(pointer.statusId).nullToVal(pointer.statusId))
                                .thenCompose(conversationId -> getConversation.invokeAsync(accountId, conversationId));
    }

     // reactive queries

     public static class HomeTimelineProxyState implements ProxyState<SortedMap> {
        public long accountId;
        public StatusPointer mostRecentStatusPointer;
        public ProxyState.Callback<SortedMap> callback;
  
        public HomeTimelineProxyState(long accountId, StatusPointer mostRecentStatusPointer, ProxyState.Callback<SortedMap> callback) {
          this.accountId = accountId;
          this.mostRecentStatusPointer = mostRecentStatusPointer;
          this.callback = callback;
        }
  
        @Override
        public SortedMap get() { throw new RuntimeException("Not implemented"); }
  
        @Override
        public void close() throws IOException { }
      }
  
  
      public void refreshHomeTimelineProxies(List<HomeTimelineProxyState> activeProxies) {
        List<List<HomeTimelineProxyState>> partitions = Lists.partition(activeProxies, 100);
        for(List<HomeTimelineProxyState> partition: partitions) {
          List tuples = new ArrayList();
          for(HomeTimelineProxyState p: partition) tuples.add(Arrays.asList(p.accountId, p.mostRecentStatusPointer));
          Map<Integer, List<StatusPointer>> res = getHomeTimelinesUntil.invoke(tuples, 50);
          for(int i=0; i<partition.size(); i++) {
            HomeTimelineProxyState p = partition.get(i);
            List<StatusPointer> pointers = res.get(i);
            if(!pointers.isEmpty()) p.mostRecentStatusPointer = pointers.get(0);
            // diff processor doesn't need old/new values since it handles KeyDiff
            for(int j=pointers.size() - 1; j>=0; j--) p.callback.change(null, new KeyDiff((long)j, new NewValueDiff(pointers.get(j))), null);
          }
        }
      }

    public CompletableFuture<HomeTimelineProxyState> proxyHomeTimeline(long accountId, ProxyState.Callback<SortedMap> callback) {
        return getHomeTimelinesUntil.invokeAsync(Arrays.asList(Arrays.asList(accountId, new StatusPointer(-1, -1))), 1)
                                    .thenApply((Map<Integer, List<StatusPointer>> m) -> {
                                       StatusPointer mostRecent = null;
                                       if(!m.get(0).isEmpty()) mostRecent = m.get(0).get(0);
                                       return new HomeTimelineProxyState(accountId, mostRecent, callback);
                                    });
    }

    public CompletableFuture<ProxyState<SortedMap>> proxyNotificationsTimeline(long accountId, ProxyState.Callback<SortedMap> callback) {
        return accountIdToNotificationsTimeline.proxyAsync(Path.key(accountId).sortedMapRangeFrom(0L, STREAM_QUERY_LIMIT), callback);
    }

    public CompletableFuture<ProxyState<SortedMap>> proxyHashtagTimeline(String hashtag, ProxyState.Callback<SortedMap> callback) {
        return hashtagToStatusPointers.proxyAsync(Path.key(hashtag).sortedMapRangeFrom(0L, STREAM_QUERY_LIMIT), callback);
    }

    public CompletableFuture<ProxyState<SortedMap>> proxyDirectTimeline(long accountId, ProxyState.Callback<SortedMap> callback) {
        return accountIdToDirectMessagesById.proxyAsync(Path.key(accountId).sortedMapRangeFrom(0L, STREAM_QUERY_LIMIT), callback);
    }

    // TODO: Idk if this works
    public CompletableFuture<List<String>> getFollowedHashtags(long accountId) {
        return hashtagToFollowers.selectAsync(Path.key(accountId).all())
                .thenApply(entries -> entries.stream()
                        .map(Object::toString)
                        .collect(Collectors.toList()));
    }

    /*
     * TODO: ESports Module Methods
     * 
     */

// New methods

public CompletableFuture<Void> fetchAndStoreSeries(String filter, String order, int skip, int take) {
    return CompletableFuture.runAsync(() -> {
        try {
            String seriesData = apiClient.getSeries(filter, order, skip, take);
            List<PostSeries> postSeriesList = parseJsonToPostSeriesList(seriesData);
            List<CompletableFuture<Boolean>> futures = new ArrayList<>();
            
            for (PostSeries postSeries : postSeriesList) {
                Series thriftSeries = convertToThriftSeries(postSeries);
                futures.add(seriesDepot.appendAsync(thriftSeries));
            }
            
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            
            // Handle pagination if needed
            if (postSeriesList.size() == take) {
                fetchAndStoreSeries(filter, order, skip + take, take);
            }
        } catch (Exception e) {
            // Log the error and potentially retry or handle it appropriately
            e.printStackTrace();
        }
    });
}

private List<PostSeries> parseJsonToPostSeriesList(String jsonData) throws Exception {
    return objectMapper.readValue(jsonData, new TypeReference<List<PostSeries>>(){});
}

    private Series convertToThriftSeries(PostSeries postSeries) {
        return new Series(
            postSeries.id,
            postSeries.title,
            postSeries.start.toEpochMilli(),
            postSeries.end.toEpochMilli(),
            postSeries.lifecycle,
            postSeries.tier,
            postSeries.bestOf,
            postSeries.chain.stream().map(this::convertChainItem).collect(Collectors.toList()),
            postSeries.streamed,
            convertBracketPosition(postSeries.bracketPosition),
            convertTournament(postSeries.tournament),
            convertSubstage(postSeries.substage),
            convertGame(postSeries.game),
            new Format(postSeries.format.bestOf),
            postSeries.participants.stream().map(this::convertParticipant).collect(Collectors.toList()),
            postSeries.matches.stream().map(this::convertMatch).collect(Collectors.toList()),
            postSeries.casters.stream().map(this::convertCaster).collect(Collectors.toList()),
            postSeries.broadcasters.stream().map(this::convertBroadcaster).collect(Collectors.toList()),
            postSeries.hasIncidentReport,
            convertGameVersion(postSeries.gameVersion),
            convertCoverage(postSeries.coverage),
            postSeries.resourceVersion
        );
    }

    private ChainItem convertChainItem(PostSeries.ChainItem item) {
        return new ChainItem(item.id);
    }

    private Tournament convertTournament(PostSeries.Tournament t) {
        return new Tournament(t.id);
    }

    private Substage convertSubstage(PostSeries.Substage s) {
        return new Substage(s.id);
    }

    private Game convertGame(PostSeries.Game g) {
        return new Game(g.id);
    }

    private BracketPosition convertBracketPosition(PostSeries.BracketPosition bp) {
        return new BracketPosition(bp.part, bp.col, bp.offset);
    }

    private Participant convertParticipant(PostSeries.Participant p) {
        return new Participant(
            p.seed,
            p.score,
            p.forfeit,
            new Roster(p.roster.id),
            p.winner,
            new ParticipantStats(p.stats.kills, p.stats.placement)
        );
    }

    private Match convertMatch(PostSeries.Match m) {
        return new Match(m.id);
    }

    private Caster convertCaster(PostSeries.Caster c) {
        return new Caster(c.primary, new CasterInfo(c.caster.id));
    }

    private Broadcaster convertBroadcaster(PostSeries.Broadcaster b) {
        return new Broadcaster(
            convertBroadcasterInfo(b.broadcaster),
            b.broadcasts.stream().map(this::convertBroadcast).collect(Collectors.toList()),
            b.official
        );
    }

    private BroadcasterInfo convertBroadcasterInfo(PostSeries.BroadcasterInfo bi) {
        return new BroadcasterInfo(
            bi.id,
            bi.name,
            bi.externalId,
            new Platform(bi.platform.id),
            new BroadcastDefaults(new Language(bi.broadcastDefaults.language.id))
        );
    }

    private Broadcast convertBroadcast(PostSeries.Broadcast b) {
        return new Broadcast(b.externalId, new Language(b.language.id));
    }

    private GameVersion convertGameVersion(PostSeries.GameVersion gv) {
        PostSeries.Release r = gv.release;
        return new GameVersion(new Release(r.uuid, r.date, r.description));
    }

    private Coverage convertCoverage(PostSeries.Coverage c) {
        PostSeries.CoverageData cd = c.data;
        return new Coverage(new CoverageData(
            convertCoverageType(cd.live),
            convertCoverageType(cd.realtime),
            convertCoverageType(cd.postgame)
        ));
    }

    private CoverageType convertCoverageType(PostSeries.CoverageType ct) {
        return new CoverageType(
            convertCoverageStatus(ct.api),
            ct.cv != null ? convertCoverageStatus(ct.cv) : null,
            ct.server != null ? convertCoverageStatus(ct.server) : null
        );
    }

    private CoverageStatus convertCoverageStatus(PostSeries.CoverageStatus cs) {
        return new CoverageStatus(cs.expectation, cs.fact);
    }
}

