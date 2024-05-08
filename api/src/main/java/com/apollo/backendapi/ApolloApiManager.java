package com.apollo.backendapi;

import clojure.lang.PersistentVector;

import java.io.*;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.time.Instant;
import java.util.AbstractMap.SimpleEntry;

import com.apollo.backend.*;
import com.apollo.backend.data.*;
import com.apollo.backend.modules.*;
import com.apollo.backendapi.pojos.*;
import com.rpl.rama.*;
import com.rpl.rama.cluster.ClusterManagerBase;
import com.rpl.rama.ops.Ops;

public class ApolloApiManager {

    private static final int MAX_PAGING_ITERATIONS = 10;
    private static final int MAX_LIMIT = 40;
    private static final int DEFAULT_LIMIT = 20;
    private static final int ANCESTORS_LIMIT = 20;
    private static final int DESCENDANTS_LIMIT = 20;

    // Modules
    public static final String CORE_MODULE_NAME = Core.class.getName();
    public static final String RELATIONSHIPS_MODULE_NAME = Relationships.class.getName();
    public static final String HASHTAGS_MODULE_NAME = TrendsAndHashtags.class.getName();
    public static final String SEARCH_MODULE_NAME = Search.class.getName();
    public static final String NOTIFICATIONS_MODULE_NAME = Notifications.class.getName();

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
    

     // Relationships Depots
    private final Depot authCodeDepot;
    private final Depot followAndBlockAccountDepot;
    private final Depot muteAccountDepot;
    private final Depot featureAccountDepot;
    private final Depot filterDepot;
    private final Depot removeFollowSuggestionDepot;
    private final Depot followHashtagDepot;


    // Notifications Depots
    private final Depot dismissDepot;
    
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

    // Hashtag/Trends PStates
    private final PState hashtagTrends;
    private final PState statusTrends;
    private final PState accountIdToHashtagActivity;

    // Search PStates
    private final PState activeAccountIds;
    private final PState newAccountIds;

    // Notifications PStates
    private final PState accountIdToNotificationsTimeline;

    // Core Queries
    private final QueryTopologyClient<List<AccountWithId>> getAccountsFromAccountIds;
    private final QueryTopologyClient<StatusQueryResults> getAccountTimeline;
    private final QueryTopologyClient<StatusQueryResults> getStatusesFromPointers;
    private final QueryTopologyClient<Conversation> getConversation;
    private final QueryTopologyClient<List<Conversation>> getConversationTimeline;
    private final QueryTopologyClient<StatusQueryResults> getAncestors;
    private final QueryTopologyClient<StatusQueryResults> getDescendants;
    private final QueryTopologyClient<List<AccountWithId>> getAccountsFromNames;
    
    // Relationship Queries
    private final QueryTopologyClient<AccountRelationshipQueryResult> getAccountRelationship;
    private final QueryTopologyClient<List<Long>> getFamiliarFollowers;
    private final QueryTopologyClient<Set<Long>> getWhoToFollowSuggestions;

    // Hashtag Queries
    private final QueryTopologyClient<Map<String, ItemStats>> batchHashtagStats;

    // Search Queries
    private final QueryTopologyClient<Map> profileTermsSearch;

    public ApolloApiManager(ClusterManagerBase cluster) {


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
        
        // Relationships Depots
        authCodeDepot = cluster.clusterDepot(RELATIONSHIPS_MODULE_NAME, "*authCodeDepot");
        followAndBlockAccountDepot = cluster.clusterDepot(RELATIONSHIPS_MODULE_NAME, "*followAndBlockAccountDepot");
        muteAccountDepot = cluster.clusterDepot(RELATIONSHIPS_MODULE_NAME, "*muteAccountDepot");
        featureAccountDepot = cluster.clusterDepot(RELATIONSHIPS_MODULE_NAME, "*featureAccountDepot");
        filterDepot = cluster.clusterDepot(RELATIONSHIPS_MODULE_NAME, "*filterDepot");
        removeFollowSuggestionDepot = cluster.clusterDepot(RELATIONSHIPS_MODULE_NAME, "*removeFollowSuggestionDepot");
        followHashtagDepot = cluster.clusterDepot(RELATIONSHIPS_MODULE_NAME, "*followHashtagDepot");

        // Notifications Depots
        dismissDepot = cluster.clusterDepot(NOTIFICATIONS_MODULE_NAME, "*dismissDepot");

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

        // Relationship PStates
        authCodeToAccountId = cluster.clusterPState(RELATIONSHIPS_MODULE_NAME, "$$authCodeToAccountId");
        followerToFolloweesById = cluster.clusterPState(RELATIONSHIPS_MODULE_NAME, "$$followerToFolloweesById");
        followeeToFollowersById = cluster.clusterPState(RELATIONSHIPS_MODULE_NAME, "$$followeeToFollowersById");
        accountIdToFollowRequests = cluster.clusterPState(RELATIONSHIPS_MODULE_NAME, "$$accountIdToFollowRequests");
        accountIdToFollowRequestsById = cluster.clusterPState(RELATIONSHIPS_MODULE_NAME, "$$accountIdToFollowRequestsById");
        accountIdToSuppressions = cluster.clusterPState(RELATIONSHIPS_MODULE_NAME, "$$accountIdToSuppressions");
        postUUIDToGeneratedId = cluster.clusterPState(RELATIONSHIPS_MODULE_NAME, "$$postUUIDToGeneratedId");
        accountIdToFilterIdToFilter = cluster.clusterPState(RELATIONSHIPS_MODULE_NAME, "$$accountIdToFilterIdToFilter");
        hashtagToFollowers = cluster.clusterPState(RELATIONSHIPS_MODULE_NAME, "$$hashtagToFollowers");

        // Hashtag/Trends PStates
        hashtagTrends = cluster.clusterPState(HASHTAGS_MODULE_NAME, "$$hashtagTrends");
        statusTrends = cluster.clusterPState(HASHTAGS_MODULE_NAME, "$$statusTrends");
        accountIdToHashtagActivity = cluster.clusterPState(HASHTAGS_MODULE_NAME, "$$accountIdToHashtagActivity");

        // Search PStates
        activeAccountIds = cluster.clusterPState(SEARCH_MODULE_NAME, "$$activeAccountIds");
        newAccountIds = cluster.clusterPState(SEARCH_MODULE_NAME, "$$newAccountIds");

        // Notifications PStates
        accountIdToNotificationsTimeline = cluster.clusterPState(NOTIFICATIONS_MODULE_NAME, "$$accountIdToNotificationsTimeline");
        
        // Core Queries
        getAccountsFromAccountIds = cluster.clusterQuery(CORE_MODULE_NAME, "getAccountsFromAccountIds");
        getAccountTimeline = cluster.clusterQuery(CORE_MODULE_NAME, "getAccountTimeline");
        getStatusesFromPointers = cluster.clusterQuery(CORE_MODULE_NAME, "getStatusesFromPointers");
        getConversation = cluster.clusterQuery(CORE_MODULE_NAME, "getConversation");
        getConversationTimeline = cluster.clusterQuery(CORE_MODULE_NAME, "getConversationTimeline");
        getAncestors = cluster.clusterQuery(CORE_MODULE_NAME, "getAncestors");
        getDescendants = cluster.clusterQuery(CORE_MODULE_NAME, "getDescendants");
        getAccountsFromNames = cluster.clusterQuery(CORE_MODULE_NAME, "getAccountsFromNames");

        // Relationships Queries
        getAccountRelationship = cluster.clusterQuery(RELATIONSHIPS_MODULE_NAME, "getAccountRelationship");
        getFamiliarFollowers = cluster.clusterQuery(RELATIONSHIPS_MODULE_NAME, "getFamiliarFollowers");
        getWhoToFollowSuggestions = cluster.clusterQuery(RELATIONSHIPS_MODULE_NAME, "getWhoToFollowSuggestions");

        // Hashtag Queries
        batchHashtagStats = cluster.clusterQuery(HASHTAGS_MODULE_NAME, "batchHashtagStats");

        // Search Queries
        profileTermsSearch = cluster.clusterQuery(SEARCH_MODULE_NAME, "profileTermsSearch");

      }

      public static class QueryResults<T, O> {
        public List<T> results;
        public boolean reachedEnd;
        public O offset; // offset to use in the next query
        public List<SimpleEntry<String, String>> linkHeaderParams; // query params to send to the client via the Link header

        public QueryResults(List<T> results, boolean reachedEnd, O offset, List<SimpleEntry<String, String>> linkHeaderParams) {
            this.results = results;
            this.reachedEnd = reachedEnd;
            this.offset = offset;
            this.linkHeaderParams = linkHeaderParams;
        }
    }

      CompletableFuture<Status> createStatusFromParams(long accountId, PostStatus params) {
        List<CompletableFuture<Object>> mediaFutures =
            params.media_ids.stream()
                            .distinct()
                            .map(attachmentId -> uuidToAttachment.selectOneAsync(Path.key(attachmentId)))
                            .collect(Collectors.toList());
        List<CompletableFuture> mentionFutures = new ArrayList<>();

        return CompletableFuture.allOf(mediaFutures.toArray(new CompletableFuture<?>[0]))
            .allOf(mentionFutures.toArray(new CompletableFuture<?>[0]))
            .thenApply(_result -> {
                List<AttachmentWithId> attachments = new ArrayList<>();
                for (int i = 0; i < mediaFutures.size(); i++) {
                  attachments.add(new AttachmentWithId(params.media_ids.get(i), (Attachment) mediaFutures.get(i).join()));
                }

                // create status
                StatusVisibility visibility = ApolloApiHelpers.createStatusVisibility(params.visibility);
                long ts = System.currentTimeMillis();
                final Status status;
                if (params.in_reply_to_id != null) {
                    StatusPointer parentPointer = ApolloHelpers.parseStatusPointer(params.in_reply_to_id);
                    ReplyStatusContent content = new ReplyStatusContent(params.status, visibility, parentPointer);
                    content.setAttachments(attachments);
                    if (params.poll != null) content.setPollContent(new PollContent(params.poll.options, ts + (params.poll.expires_in * 1000), params.poll.multiple));
                    if (params.sensitive != null && params.sensitive) content.setSensitiveWarning(params.spoiler_text != null ? params.spoiler_text : "");
                    status = new Status(accountId, StatusContent.reply(content), ts);
                } else {
                    NormalStatusContent content = new NormalStatusContent(params.status, visibility);
                    content.setAttachments(attachments);
                    if (params.poll != null) content.setPollContent(new PollContent(params.poll.options, ts + (params.poll.expires_in * 1000), params.poll.multiple));
                    if (params.sensitive != null && params.sensitive) content.setSensitiveWarning(params.spoiler_text != null ? params.spoiler_text : "");
                    status = new Status(accountId, StatusContent.normal(content), ts);
                }

                return status;
            });
    }

      public static CompletableFuture<StatusQueryResults> queryStatusesWithPaging(BiFunction<StatusPointer, Integer, CompletableFuture<StatusQueryResults>> fn, StatusPointer offsetMaybe, Integer limitMaybe, int iterationsLeft) {
        if (iterationsLeft == 0) return CompletableFuture.completedFuture(new StatusQueryResults(new ArrayList(), new HashMap(), true, false));

        StatusPointer offset = offsetMaybe == null ? new StatusPointer(-1, -1) : offsetMaybe;
        int limit = Math.min(limitMaybe == null ? DEFAULT_LIMIT : limitMaybe, MAX_LIMIT);

        return fn.apply(offset, limit)
                 .thenCompose(statusQueryResults -> {
                     // if the results are less than the limit and we haven't reached the end...
                     if (statusQueryResults.results.size() < limit && !statusQueryResults.reachedEnd) {
                         StatusPointer nextOffset;
                         int nextLimit = limit - statusQueryResults.results.size();
                         if (statusQueryResults.isSetLastStatusPointer()) nextOffset = statusQueryResults.lastStatusPointer;
                         else return CompletableFuture.completedFuture(statusQueryResults);
                         // recursively make the new request and concat the results.
                         return queryStatusesWithPaging(fn, nextOffset, nextLimit, iterationsLeft-1)
                                .thenApply(nextResults -> {
                                    List<StatusResultWithId> results = new ArrayList<>(statusQueryResults.results);
                                    results.addAll(nextResults.results);
                                    HashMap<String, AccountWithId> mentions = new HashMap<>(statusQueryResults.mentions);
                                    mentions.putAll(nextResults.mentions);
                                    StatusQueryResults combinedResults = new StatusQueryResults(results, mentions, nextResults.reachedEnd, nextResults.refreshed);
                                    if (nextResults.isSetLastStatusPointer()) combinedResults.setLastStatusPointer(nextResults.lastStatusPointer);
                                    return combinedResults;
                                });
                     } else return CompletableFuture.completedFuture(statusQueryResults);
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
                                            if (accountWithIds.size() == 0) return null;
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
        return accountDepot.appendAsync(new Account(params.username, params.email, pwdHash, params.locale, uuid, keys.publicKey, System.currentTimeMillis()))
                           .thenCompose(res -> this.getAccountUUID(params.username))
                           .thenApply(accountUUID -> accountUUID.equals(uuid));
    }

    public CompletableFuture<String> getAccountUUID(String username) {
        return nameToUser.selectOneAsync(Path.key(username, "uuid"));
    }

      public CompletableFuture<StatusQueryResults> getAccountTimeline(Long requestAccountIdMaybe, long timelineAccountId, StatusPointer offsetMaybe, Integer limitMaybe, boolean includeReplies, boolean includeBoosts) {
        return this.getPinnedStatuses(requestAccountIdMaybe, timelineAccountId)
                   .thenCompose(pinnedStatuses -> {
                       Set<Long> pinnedIds = pinnedStatuses.results.stream().map(o -> o.statusId).collect(Collectors.toSet());
                       return queryStatusesWithPaging((offset, limit) ->
                               getAccountTimeline.invokeAsync(requestAccountIdMaybe, timelineAccountId, offset.statusId, limit, includeReplies)
                                                 .thenApply(statusQueryResults -> {
                                                     if (pinnedIds.size() > 0) statusQueryResults.results = statusQueryResults.results.stream().filter(statusResult -> !pinnedIds.contains(statusResult.statusId)).collect(Collectors.toList());
                                                     if (!includeBoosts) statusQueryResults.results = statusQueryResults.results.stream().filter(statusResult -> !statusResult.status.content.isSetBoost()).collect(Collectors.toList());
                                                     return statusQueryResults;
                                                 }),
                               offsetMaybe, limitMaybe, MAX_PAGING_ITERATIONS);
                   });
    }

    

     
    public CompletableFuture<StatusQueryResults> getPinnedStatuses(Long requestAccountIdMaybe, long authorId) {
        return pinnerToStatusIds.selectAsync(Path.key(authorId).mapVals())
                                .thenCompose(statusIds -> {
                                    List<StatusPointer> pointers = new ArrayList<>();
                                    for (Object statusId : statusIds) pointers.add(new StatusPointer(authorId, (Long) statusId));
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
                if (statusId == null) return CompletableFuture.completedFuture(null);
                StatusPointer newPointer = new StatusPointer(accountId, (Long) statusId);
                return this.getStatus(accountId, newPointer);
          });
    }

    public CompletableFuture<StatusWithId> postScheduledStatus(long accountId, PostStatus params, Object object) {
        String uuid = UUID.randomUUID().toString();
        return createStatusFromParams(accountId, params)
            .thenComposeAsync(status -> {
                AddScheduledStatus addScheduledStatus = new AddScheduledStatus(uuid, status, Instant.parse(params.scheduled_at).toEpochMilli());
                return scheduledStatusDepot.appendAsync(addScheduledStatus);
            })
            .thenCompose(res -> postUUIDToStatusId.selectOneAsync(accountId, Path.key(uuid)))
            .thenCompose(statusId -> {
                if (statusId == null) return CompletableFuture.completedFuture(null);
                return accountIdToScheduledStatuses.selectOneAsync(Path.key(accountId, statusId, "status"))
                                                   .thenApply(status -> new StatusWithId((long) statusId, (Status) status));
            });
    }

    public CompletableFuture<StatusQueryResult> getStatus(Long requestAccountIdMaybe, StatusPointer pointer, QueryFilterOptions filterOptions) {
        return getStatusesFromPointers.invokeAsync(requestAccountIdMaybe, PersistentVector.EMPTY.cons(new StatusPointer(pointer.authorId, pointer.statusId)), filterOptions)
                                      .thenApply(statusQueryResults -> {
                                          if (statusQueryResults.results.size() == 0) return null;
                                          StatusResultWithId result = statusQueryResults.results.get(0);
                                          return new StatusQueryResult(result, statusQueryResults.mentions);
                                      });
    }

    public CompletableFuture<StatusQueryResult> getStatus(Long requestAccountIdMaybe, StatusPointer pointer) {
        QueryFilterOptions filterOptions = new QueryFilterOptions(FilterContext.Public, false);
        return this.getStatus(requestAccountIdMaybe, pointer, filterOptions);
    }

    public CompletableFuture<StatusWithId> getScheduledStatus(StatusPointer statusPointer) {
        return accountIdToScheduledStatuses.selectOneAsync(Path.key(statusPointer.authorId, statusPointer.statusId, "status"))
                                           .thenApply(status -> {
                                               if (status == null) return null;
                                               return new StatusWithId(statusPointer.statusId, (Status) status);
                                           });
      }

      public CompletableFuture<QueryResults<StatusWithId, Long>> getScheduledStatuses(Long accountId, StatusPointer offsetMaybe, Integer limitMaybe) {
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
                    List<StatusWithId> statuses =
                            results.stream()
                                   .map(result -> {
                                      List<Object> resultList = (List<Object>) result;
                                      Long statusId = (Long) resultList.get(0);
                                      Status status = (Status) resultList.get(1);
                                      return new StatusWithId(statusId, status);
                                   }).collect(Collectors.toList());
                    Long lastId = null;
                    List<SimpleEntry<String, String>> linkHeaderParams = null;
                    if (statuses.size() > 0) {
                        StatusWithId lastStatus = statuses.get(statuses.size()-1);
                        lastId = lastStatus.statusId;
                        linkHeaderParams = Arrays.asList(new SimpleEntry<>("max_id", ApolloHelpers.serializeStatusPointer(new StatusPointer(lastStatus.status.authorId, lastStatus.statusId))));
                    }
                    return new QueryResults<>(statuses, results.size() < limit, lastId, linkHeaderParams);
                });
    }

    public CompletableFuture<StatusWithId> updateScheduledStatus(StatusPointer statusPointer, String scheduledAt) {
        long publishAt = Instant.parse(scheduledAt).toEpochMilli();
        long timestamp = Instant.now().toEpochMilli();
        return scheduledStatusDepot.appendAsync(new EditScheduledStatusPublishTime(statusPointer.authorId, statusPointer.statusId, publishAt, timestamp))
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
        return scheduledStatusDepot.appendAsync(new RemoveStatus(statusPointer.authorId, statusPointer.statusId, Instant.now().toEpochMilli()));
    }

    public CompletableFuture<SimpleEntry<AccountWithId, AccountWithId>> getAccountWithIdPair(long firstAccountId, long secondAccountId) {
        return getAccountsFromAccountIds.invokeAsync(null, Arrays.asList(firstAccountId, secondAccountId))
                                        .thenApply(accountWithIds -> {
                                            if (accountWithIds.size() != 2) return null;
                                            return new SimpleEntry<>(accountWithIds.get(0), accountWithIds.get(1));
                                        });
    }

    public CompletableFuture<Boolean> postFollowAccount(long followerId, long followeeId, PostFollow params) {
        return getAccountWithId(followeeId)
            .thenCompose((followee) -> {
                if (followee != null && followee.account != null && followee.account.locked) {
                    FollowLockedAccount req = new FollowLockedAccount(followeeId, followerId, System.currentTimeMillis());
                    if (params != null) {
                        if (params.reposts != null) req.setShowBoosts(params.reposts);
                        if (params.notify != null) req.setNotify(params.notify);
                        if (params.languages != null) req.setLanguages(params.languages);
                    }
                    return followAndBlockAccountDepot.appendAsync(req);
                } else {
                    FollowAccount req = new FollowAccount(followerId, followeeId, System.currentTimeMillis());
                    if (params != null) {
                        if (params.reposts != null ) req.setShowBoosts(params.reposts);
                        if (params.notify != null) req.setNotify(params.notify);
                        if (params.languages != null) req.setLanguages(params.languages);
                    }
                    return followAndBlockAccountDepot.appendAsync(req);
                }
            }).thenApply(res -> true);
    }

    public CompletableFuture<AccountRelationshipQueryResult> getAccountRelationship(long sourceId, long targetId) {
        return getAccountRelationship.invokeAsync(sourceId, targetId);
    }

    public CompletableFuture<Boolean> postRemoveFollowAccount(long followerId, long followeeId) {
        RemoveFollowAccount removeFollowAccount = new RemoveFollowAccount(followerId, followeeId, System.currentTimeMillis());
        return followAndBlockAccountDepot.appendAsync(removeFollowAccount).thenApply(res -> true);
    }

    public CompletableFuture<Boolean> postMuteAccount(long muterId, long muteeId, PostMute params) {
        MuteAccountOptions options = new MuteAccountOptions(params.notifications);
        if(params.duration != null) options.setExpirationMillis(System.currentTimeMillis() + params.duration * 1000);
        return muteAccountDepot.appendAsync(new MuteAccount(muterId, muteeId, options, System.currentTimeMillis())).thenApply(res -> true);
    }

    public CompletableFuture<Boolean> postRemoveMuteAccount(long muterId, long muteeId) {
        return muteAccountDepot.appendAsync(new RemoveMuteAccount(muterId, muteeId, System.currentTimeMillis())).thenApply(res -> true);
    }

    public CompletableFuture<Boolean> postBlockAccount(long blockerId, long blockeeId) {
        return followAndBlockAccountDepot.appendAsync(new BlockAccount(blockerId, blockeeId, System.currentTimeMillis()))
                                         .thenApply(res -> true);
    }

    public CompletableFuture<Boolean> postRemoveBlockAccount(long blockerId, long blockeeId) {
        return followAndBlockAccountDepot.appendAsync(new RemoveBlockAccount(blockerId, blockeeId, System.currentTimeMillis()))
                                         .thenApply(res -> true);
    }

    public CompletableFuture<Boolean> postFeatureAccount(long featurerId, long featureeId) {
        return featureAccountDepot.appendAsync(new FeatureAccount(featurerId, featureeId, System.currentTimeMillis())).thenApply(res -> true);
    }

    public CompletableFuture<Boolean> postRemoveFeatureAccount(long featurerId, long featureeId) {
        return featureAccountDepot.appendAsync(new RemoveFeatureAccount(featurerId, featureeId, System.currentTimeMillis()))
                                  .thenApply(res -> true);
    }

    public CompletableFuture<Conversation> postConversation(long accountId, long conversationId, boolean unread) {
        return conversationDepot.appendAsync(new EditConversation(accountId, conversationId, unread))
                                .thenCompose(result -> getConversation.invokeAsync(accountId, conversationId)
                                                                      .thenApply(convoMaybe -> {
                                                                         if (convoMaybe == null) return null;
                                                                         // the change was processed in a microbatch
                                                                         // so the query won't necessarily return the
                                                                         // most up-to-date value, so we're updating it manually.
                                                                         convoMaybe.unread = unread;
                                                                         return convoMaybe;
                                                                      }));
    }

    public CompletableFuture<QueryResults<Conversation, Long>> getConversationTimeline(long accountId, Long offsetMaybe, Integer limitMaybe) {
        CompletableFuture<Long> timelineIndexFuture =
                offsetMaybe == null ? CompletableFuture.completedFuture(-1L)
                                    : accountIdToConvoIds.selectOneAsync(Path.key(accountId, offsetMaybe).nullToVal(-1L));
        int limit = Math.min(limitMaybe == null ? DEFAULT_LIMIT : limitMaybe, MAX_LIMIT);
        return timelineIndexFuture.thenCompose(timelineIndex ->
                getConversationTimeline.invokeAsync(accountId, timelineIndex, limit)
                                       .thenApply(conversations -> {
                                           Long lastId = null;
                                           List<SimpleEntry<String, String>> linkHeaderParams = null;
                                           if (conversations.size() > 0) {
                                               lastId = conversations.get(conversations.size()-1).conversationId;
                                               linkHeaderParams = Arrays.asList(new SimpleEntry<>("max_id", lastId+""));
                                           }
                                           return new QueryResults<>(conversations, conversations.size() < limit, lastId, linkHeaderParams);
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

    public CompletableFuture<StatusQueryResults> getTrendingStatuses(Long requestAccountIdMaybe, Integer limitMaybe, Integer offsetMaybe) {
        long offset = offsetMaybe == null ? 0 : offsetMaybe;
        int limit = Math.min(limitMaybe == null ? DEFAULT_LIMIT : limitMaybe, MAX_LIMIT);
        return statusTrends.selectAsync(Path.all().first())
                           .thenApply(statuses -> statuses.stream().skip(offset).limit(limit).collect(Collectors.toList()))
                           .thenCompose(statusPointers -> getStatusesFromPointers.invokeAsync(requestAccountIdMaybe, statusPointers, new QueryFilterOptions(FilterContext.Public, false)));
    }

    public CompletableFuture<QueryResults<AccountWithId, Long>> getAccountFollowees(long followerId, Long offsetMaybe, Integer limitMaybe) {
        long offset = offsetMaybe == null ? -1L : offsetMaybe;
        int limit = Math.min(limitMaybe == null ? DEFAULT_LIMIT : limitMaybe, MAX_LIMIT);
        SortedRangeFromOptions options = SortedRangeFromOptions.excludeStart().maxAmt(limit);
        CompletableFuture<List<List>> followeesFuture = followerToFolloweesById.selectAsync(Path.key(followerId).sortedMapRangeFrom(offset, options).all());
        return followeesFuture.thenCompose(keyVals -> getAccountWithTimelineIndexes(keyVals, limit))
                              .thenApply(accountWithTimelineIndexes -> {
                                  List<SimpleEntry<Long, AccountWithId>> results = accountWithTimelineIndexes.getKey();
                                  List<AccountWithId> accountWithIds = results.stream().map(SimpleEntry::getValue).collect(Collectors.toList());
                                  boolean reachedEnd = accountWithTimelineIndexes.getValue();
                                  Long lastId = null;
                                  List<SimpleEntry<String, String>> linkHeaderParams = null;
                                  if (results.size() > 0) {
                                      lastId = results.get(results.size()-1).getKey();
                                      linkHeaderParams = Arrays.asList(new SimpleEntry<>("max_id", lastId+""));
                                  }
                                  return new QueryResults<>(accountWithIds, reachedEnd, lastId, linkHeaderParams);
                              });
    }

    public CompletableFuture<QueryResults<AccountWithId, Long>> getAccountFollowers(long followeeId, Long offsetMaybe, Integer limitMaybe) {
        long offset = offsetMaybe == null ? -1L : offsetMaybe;
        int limit = Math.min(limitMaybe == null ? DEFAULT_LIMIT : limitMaybe, MAX_LIMIT);
        SortedRangeFromOptions options = SortedRangeFromOptions.excludeStart().maxAmt(limit);
        CompletableFuture<List<List>> followeesFuture = followeeToFollowersById.selectAsync(Path.key(followeeId).sortedMapRangeFrom(offset, options).all());
        return followeesFuture.thenCompose(keyVals -> getAccountWithTimelineIndexes(keyVals, limit))
                              .thenApply(accountWithTimelineIndexes -> {
                                  List<SimpleEntry<Long, AccountWithId>> results = accountWithTimelineIndexes.getKey();
                                  List<AccountWithId> accountWithIds = results.stream().map(SimpleEntry::getValue).collect(Collectors.toList());
                                  boolean reachedEnd = accountWithTimelineIndexes.getValue();
                                  Long lastId = null;
                                  List<SimpleEntry<String, String>> linkHeaderParams = null;
                                  if (results.size() > 0) {
                                      lastId = results.get(results.size()-1).getKey();
                                      linkHeaderParams = Arrays.asList(new SimpleEntry<>("max_id", lastId+""));
                                  }
                                  return new QueryResults<>(accountWithIds, reachedEnd, lastId, linkHeaderParams);
                              });
    }

    public CompletableFuture<SimpleEntry<List<SimpleEntry<Long, AccountWithId>>, Boolean>> getAccountWithTimelineIndexes(List<List> keyVals, long limit) {
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

    public CompletableFuture<QueryResults<AccountWithId, Long>> getFollowRequests(long requestAccountId, Long offsetMaybe, Integer limitMaybe) {
        return queryWithPaging(
            (offset, limit) -> {
                SortedRangeFromOptions options = SortedRangeFromOptions.excludeStart().maxAmt(limit);
                CompletableFuture<List<Long>> future = accountIdToFollowRequestsById.selectAsync(Path.key(requestAccountId).sortedMapRangeFrom(offset, options).mapVals().customNav(new com.apollo.backend.navs.TField("requesterId")));
                return future.thenCompose(requesterIds -> getAccountsFromAccountIds.invokeAsync(requestAccountId, requesterIds))
                             .thenApply(accountWithIds -> {
                                 Long lastId = null;
                                 List<SimpleEntry<String, String>> linkHeaderParams = null;
                                 if (accountWithIds.size() > 0) {
                                     AccountWithId lastAccount = accountWithIds.get(accountWithIds.size()-1);
                                     lastId = lastAccount.accountId;
                                     linkHeaderParams = Arrays.asList(new SimpleEntry<>("max_id", ApolloHelpers.serializeAccountId(lastAccount.accountId)));
                                 }
                                 return new QueryResults<>(accountWithIds, accountWithIds.size() < limit, lastId, linkHeaderParams);
                             });
            },
            offsetMaybe == null ? -1L : offsetMaybe,
            Math.min(limitMaybe == null ? DEFAULT_LIMIT : limitMaybe, MAX_LIMIT),
            MAX_PAGING_ITERATIONS
        );
    }

    public CompletableFuture<Boolean> acceptFollowRequest(long accountId, long requesterId) {
        return accountIdToFollowRequests.selectOneAsync(Path.key(accountId, requesterId))
                                        .thenCompose(existingRequest -> {
                                            if (existingRequest != null) {
                                                return followAndBlockAccountDepot.appendAsync(new AcceptFollowRequest(accountId, requesterId, System.currentTimeMillis()))
                                                                                 .thenApply(res -> true);
                                            } else return CompletableFuture.completedFuture(false);
                                        });
    }

    public CompletableFuture<Boolean> rejectFollowRequest(long accountId, long requesterId) {
        return accountIdToFollowRequests.selectOneAsync(Path.key(accountId, requesterId))
                                        .thenCompose(existingRequest -> {
                                            if (existingRequest != null) {
                                                return followAndBlockAccountDepot.appendAsync(new RejectFollowRequest(accountId, requesterId))
                                                                                 .thenApply(res -> true);
                                            } else return CompletableFuture.completedFuture(false);
                                        });
    }

    public static <T, O> CompletableFuture<QueryResults<T, O>> queryWithPaging(BiFunction<O, Integer, CompletableFuture<QueryResults<T, O>>> fn, O offset, int limit, int iterationsLeft) {
        if (iterationsLeft == 0) return CompletableFuture.completedFuture(new QueryResults<>(new ArrayList<>(), true, null, null));

        return fn.apply(offset, limit)
                 .thenCompose(queryResults -> {
                     // if the results are less than the limit and we haven't reached the end...
                     if (queryResults.results.size() < limit && !queryResults.reachedEnd) {
                         O nextOffset;
                         int nextLimit = limit - queryResults.results.size();
                         if (queryResults.offset != null) nextOffset = queryResults.offset;
                         else return CompletableFuture.completedFuture(queryResults);
                         // recursively make the new request and concat the results.
                         return queryWithPaging(fn, nextOffset, nextLimit, iterationsLeft-1)
                                .thenApply(nextResults -> {
                                    List<T> results = new ArrayList<>(queryResults.results);
                                    results.addAll(nextResults.results);
                                    return new QueryResults<>(results, nextResults.reachedEnd, nextResults.offset, nextResults.linkHeaderParams);
                                });
                     } else return CompletableFuture.completedFuture(queryResults);
                 });
    }

    public CompletableFuture<QueryResults<AccountWithId, Long>> getBlocks(long blockerId, Long offsetMaybe, Integer limitMaybe) {
        return queryWithPaging(
            (offset, limit) -> {
                SortedRangeFromOptions options = SortedRangeFromOptions.excludeStart().maxAmt(limit);
                CompletableFuture<List<Long>> blockeeIdsFuture = accountIdToSuppressions.selectAsync(Path.key(blockerId, "blocked").sortedSetRangeFrom(offset, options).all());
                return blockeeIdsFuture.thenCompose(this::getAccountsFromAccountIds)
                                       .thenApply(accountWithIds -> {
                                           Long lastId = null;
                                           List<SimpleEntry<String, String>> linkHeaderParams = null;
                                           if (accountWithIds.size() > 0) {
                                               lastId = accountWithIds.get(accountWithIds.size()-1).accountId;
                                               linkHeaderParams = Arrays.asList(new SimpleEntry<>("max_id", ApolloHelpers.serializeAccountId(lastId)));
                                           }
                                           return new QueryResults<>(accountWithIds, accountWithIds.size() < limit, lastId, linkHeaderParams);
                                       });
            },
            offsetMaybe == null ? -1L : offsetMaybe,
            Math.min(limitMaybe == null ? DEFAULT_LIMIT : limitMaybe, MAX_LIMIT),
            MAX_PAGING_ITERATIONS
        );
    }

    public CompletableFuture<StatusQueryResult> postLikeStatus(long favoriterId, StatusPointer pointer) {
        return likeStatusDepot.appendAsync(new LikeStatus(favoriterId, pointer, System.currentTimeMillis()))
                                  .thenCompose(res -> this.getStatus(favoriterId, pointer))
                                  .thenApply(resultMaybe -> {
                                      if (resultMaybe == null) return null;
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
                                      if (resultMaybe == null) return null;
                                      // the change was processed in a microbatch
                                      // so the query won't necessarily return the
                                      // most up-to-date value, so we're updating it manually.
                                      StatusQueryResult statusQueryResult = (StatusQueryResult) resultMaybe;
                                      statusQueryResult.result.status.metadata.liked = false;
                                      return statusQueryResult;
                                  });
    }

    public CompletableFuture<StatusQueryResult> postBoostStatus(long boosterId, StatusPointer pointer) {
        BoostStatus boostStatus = new BoostStatus(UUID.randomUUID().toString(), boosterId, pointer, System.currentTimeMillis());
        return statusDepot.appendAsync(boostStatus)
                          .thenCompose(res -> this.getStatus(boosterId, pointer))
                          .thenApply(resultMaybe -> {
                              if (resultMaybe == null) return null;
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
                              if (resultMaybe == null) return null;
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
                                      if (resultMaybe == null) return null;
                                      // the change was processed in a microbatch
                                      // so the query won't necessarily return the
                                      // most up-to-date value, so we're updating it manually.
                                      StatusQueryResult statusQueryResult = (StatusQueryResult) resultMaybe;
                                      statusQueryResult.result.status.metadata.bookmarked = true;
                                      return statusQueryResult;
                                  });
    }

    public CompletableFuture<StatusQueryResult> postRemoveBookmarkStatus(long bookmarkerId, StatusPointer pointer) {
        return bookmarkStatusDepot.appendAsync(new RemoveBookmarkStatus(bookmarkerId, pointer, System.currentTimeMillis()))
                                  .thenCompose(res -> this.getStatus(bookmarkerId, pointer))
                                  .thenApply(resultMaybe -> {
                                      if (resultMaybe == null) return null;
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
                                  if (resultMaybe == null) return null;
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
                                  if (resultMaybe == null) return null;
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
                                 if (resultMaybe == null) return null;
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
                                 if (resultMaybe == null) return null;
                                 // the change was processed in a microbatch
                                 // so the query won't necessarily return the
                                 // most up-to-date value, so we're updating it manually.
                                 StatusQueryResult statusQueryResult = (StatusQueryResult) resultMaybe;
                                 statusQueryResult.result.status.metadata.pinned = false;
                                 return statusQueryResult;
                             });
    }

    public CompletableFuture<StatusQueryResult> putStatus(StatusPointer statusPointer, PutStatus params) {
        List<CompletableFuture<Object>> mediaFutures =
            params.media_ids.stream()
                            .distinct()
                            .map(attachmentId -> uuidToAttachment.selectOneAsync(Path.key(attachmentId)))
                            .collect(Collectors.toList());
        return CompletableFuture.allOf(mediaFutures.toArray(new CompletableFuture<?>[0]))
                .thenCompose(_result -> {
                    List<AttachmentWithId> attachments = new ArrayList<>();
                    for (int i = 0; i < mediaFutures.size(); i++) {
                      attachments.add(new AttachmentWithId(params.media_ids.get(i), (Attachment) mediaFutures.get(i).join()));
                    }
                    return accountIdToStatuses.selectOneAsync(Path.key(statusPointer.authorId, statusPointer.statusId).first())
                                              .thenCompose(statusMaybe -> {
                                                  if (statusMaybe == null) return CompletableFuture.completedFuture(null);
                                                  Status edit = (Status) statusMaybe;
                                                  if (edit.content.isSetNormal()) {
                                                      NormalStatusContent content = edit.content.getNormal();
                                                      content.text = params.status;
                                                      content.setAttachments(attachments);
                                                      if (params.poll != null && content.isSetPollContent()) content.setPollContent(new PollContent(params.poll.options, content.pollContent.expirationMillis, params.poll.multiple));
                                                      if (params.sensitive != null && params.sensitive) content.setSensitiveWarning(params.spoiler_text != null ? params.spoiler_text : "");
                                                      else content.unsetSensitiveWarning();
                                                  }
                                                  else if (edit.content.isSetReply()) {
                                                      ReplyStatusContent content = edit.content.getReply();
                                                      content.text = params.status;
                                                      content.setAttachments(attachments);
                                                      if (params.poll != null && content.isSetPollContent()) content.setPollContent(new PollContent(params.poll.options, content.pollContent.expirationMillis, params.poll.multiple));
                                                      if (params.sensitive != null && params.sensitive) content.setSensitiveWarning(params.spoiler_text != null ? params.spoiler_text : "");
                                                      else content.unsetSensitiveWarning();
                                                  }
                                                  else if (edit.content.isSetBoost()) return CompletableFuture.completedFuture(null); // you can't edit boosts
                                                  return statusDepot.appendAsync(new EditStatus(statusPointer.statusId, edit))
                                                                    .thenCompose(res -> this.getStatus(statusPointer.authorId, statusPointer));
                                              });
            });
    }

    public CompletableFuture<Boolean> deleteStatus(long accountId, long statusId) {
        return statusDepot.appendAsync(new RemoveStatus(accountId, statusId, System.currentTimeMillis())).thenApply(res -> true);
    }

    public CompletableFuture<StatusQueryResults> getAncestors(Long requestAccountIdMaybe, StatusPointer pointer) {
        return getAncestors.invokeAsync(requestAccountIdMaybe, pointer.authorId, pointer.statusId, ANCESTORS_LIMIT);
    }

    public CompletableFuture<StatusQueryResults> getDescendants(Long requestAccountIdMaybe, StatusPointer pointer) {
        return getDescendants.invokeAsync(requestAccountIdMaybe, pointer.authorId, pointer.statusId, DESCENDANTS_LIMIT);
    }

    public CompletableFuture<StatusQueryResults> getBookmarks(long bookmarkerId, StatusPointer offsetMaybe, Integer limitMaybe) {
        return queryStatusesWithPaging((offset, limit) -> {
                    SortedRangeFromOptions options = SortedRangeFromOptions.excludeStart().maxAmt(limit);
                    return bookmarkerToStatusPointers.selectAsync(Path.key(bookmarkerId).sortedMapRangeFrom(offset, options).mapKeys())
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

    public CompletableFuture<QueryResults<AccountWithId, Long>> getStatusBoosters(Long requestAccountIdMaybe, long authorId, long statusId, Long offsetMaybe, Integer limitMaybe) {
        long offset = offsetMaybe == null ? -1L : offsetMaybe;
        int defaultLimit = 40;
        int maxLimit = 80;
        int limit = Math.min(limitMaybe == null ? defaultLimit : limitMaybe, maxLimit);
        // make sure requester is allowed to see status
        return this.getStatus(requestAccountIdMaybe, new StatusPointer(authorId, statusId))
                   .thenCompose(resultMaybe -> {
                       if (resultMaybe == null) return CompletableFuture.completedFuture(null);
                       // get the boosters
                       SortedRangeFromOptions options = SortedRangeFromOptions.excludeStart().maxAmt(limit);
                       CompletableFuture<List<Long>> boosterIdsFuture = statusIdToBoosters.selectAsync(authorId, Path.key(statusId).sortedMapRangeFrom(offset, options).mapKeys());
                       return boosterIdsFuture.thenCompose(this::getAccountsFromAccountIds)
                                              .thenApply(accountWithIds -> {
                                                  Long lastId = null;
                                                  List<SimpleEntry<String, String>> linkHeaderParams = null;
                                                  if (accountWithIds.size() > 0) {
                                                      lastId = accountWithIds.get(accountWithIds.size()-1).accountId;
                                                      linkHeaderParams = Arrays.asList(new SimpleEntry<>("max_id", ApolloHelpers.serializeAccountId(lastId)));
                                                  }
                                                  return new QueryResults<>(accountWithIds, accountWithIds.size() < limit, lastId, linkHeaderParams);
                                              });
                   });
    }

    public CompletableFuture<QueryResults<AccountWithId, Long>> getStatusLikers(Long requestAccountIdMaybe, long authorId, long statusId, Long offsetMaybe, Integer limitMaybe) {
        long offset = offsetMaybe == null ? -1L : offsetMaybe;
        int defaultLimit = 40;
        int maxLimit = 80;
        int limit = Math.min(limitMaybe == null ? defaultLimit : limitMaybe, maxLimit);
        // make sure requester is allowed to see status
        return this.getStatus(requestAccountIdMaybe, new StatusPointer(authorId, statusId))
                   .thenCompose(resultMaybe -> {
                       if (resultMaybe == null) return CompletableFuture.completedFuture(null);
                       // get the favoriters
                       SortedRangeFromOptions options = SortedRangeFromOptions.excludeStart().maxAmt(limit);
                       CompletableFuture<List<Long>> favoriterIdsFuture = statusIdToLikers.selectAsync(authorId, Path.key(statusId).sortedMapRangeFrom(offset, options).mapKeys());
                       return favoriterIdsFuture.thenCompose(this::getAccountsFromAccountIds)
                                                .thenApply(accountWithIds -> {
                                                    Long lastId = null;
                                                    List<SimpleEntry<String, String>> linkHeaderParams = null;
                                                    if (accountWithIds.size() > 0) {
                                                        AccountWithId lastAccount = accountWithIds.get(accountWithIds.size()-1);
                                                        lastId = lastAccount.accountId;
                                                        linkHeaderParams = Arrays.asList(new SimpleEntry<>("max_id", ApolloHelpers.serializeAccountId(lastAccount.accountId)));
                                                    }
                                                    return new QueryResults<>(accountWithIds, accountWithIds.size() < limit, lastId, linkHeaderParams);
                                                });
                   });
    }

    public CompletableFuture<QueryResults<AccountWithId, Map>> getProfileSearch(long requestAccountId, List<String> terms, Map startParamsMaybe, Integer limitMaybe, boolean followeesOnly) {
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
                                                       if (followeesOnly) accountWithIds = accountWithIds.stream().filter(o -> o.metadata.isFollowedByRequester).collect(Collectors.toList());
                                                       return new QueryResults<>(accountWithIds, nextParams == null, nextParams, ApolloApiHelpers.createLinkHeaderParams(nextParams));
                                                   });
                    });
                },
                startParamsMaybe,
                Math.min(limitMaybe == null ? defaultLimit : limitMaybe, maxLimit),
                MAX_PAGING_ITERATIONS)
          .thenCompose(result -> {
            if(terms.size() == 1 && result.results.isEmpty()) {
              // override prefix search
              List<String> terms2 = Arrays.asList(terms.get(0), terms.get(0));
              return getProfileSearch(requestAccountId, terms2, startParamsMaybe, limitMaybe, followeesOnly);
            } else {
                // deduplicate results
                LinkedHashMap<Long, AccountWithId> dedupedResults = new LinkedHashMap<>();
                for (AccountWithId awid : result.results) dedupedResults.put(awid.accountId, awid);
                result.results = new ArrayList<>(dedupedResults.values());
                return CompletableFuture.completedFuture(result);
            }
          });
    }

    public CompletableFuture<Boolean> postEditAccount(long accountId, List<EditAccountField> edits) {
        if (edits.size() == 0) return CompletableFuture.completedFuture(true);
        return accountEditDepot.appendAsync(new EditAccount(accountId, edits, System.currentTimeMillis())).thenApply(res -> true);
    }

    public CompletableFuture<List<AccountWithId>> getFamiliarFollowers(long requestAccountId, long targetId) {
        CompletableFuture<List<Long>> familiarFollowersFuture = getFamiliarFollowers.invokeAsync(requestAccountId, targetId);
        return familiarFollowersFuture.thenCompose(this::getAccountsFromAccountIds);
    }

    public CompletableFuture<StatusQueryResults> getAttachmentStatuses(Long requestAccountIdMaybe, long authorId, StatusPointer offsetMaybe, Integer limitMaybe) {
        return queryStatusesWithPaging((offset, limit) -> {
                  SortedRangeFromOptions options = SortedRangeFromOptions.excludeStart().maxAmt(limit);
                  return accountIdToAttachmentStatusIds.selectAsync(Path.key(authorId).sortedSetRangeFrom(offset.statusId, options).all())
                                                       .thenCompose(statusIds -> {
                                                           List<StatusPointer> pointers = new ArrayList<>();
                                                           for (Object statusId : statusIds) pointers.add(new StatusPointer(authorId, (Long) statusId));
                                                           QueryFilterOptions filterOptions = new QueryFilterOptions(FilterContext.Public, false);
                                                           return getStatusesFromPointers.invokeAsync(requestAccountIdMaybe, pointers, filterOptions);
                                                       });
                }, offsetMaybe, limitMaybe, MAX_PAGING_ITERATIONS);
    }

    public CompletableFuture<StatusQueryResults> getTaggedStatuses(Long requestAccountIdMaybe, long authorId, String hashtag, StatusPointer offsetMaybe, Integer limitMaybe) {
        return queryStatusesWithPaging((offset, limit) -> {
                  SortedRangeFromOptions rangeOptions = SortedRangeFromOptions.excludeStart().maxAmt(limit);
                  return accountIdToHashtagActivity.selectAsync(Path.key(authorId, hashtag, "timeline").sortedSetRangeFrom(offset.statusId, rangeOptions).all())
                          .thenCompose((List<Object> statusIds) -> {
                              QueryFilterOptions filterOptions = new QueryFilterOptions(FilterContext.Public, false);
                              List<StatusPointer> pointers = statusIds.stream()
                                                                      .map((statusId) -> new StatusPointer(authorId, (Long) statusId))
                                                                      .collect(Collectors.toList());
                              return getStatusesFromPointers.invokeAsync(requestAccountIdMaybe, pointers, filterOptions);
                          });
                }, offsetMaybe, limitMaybe, MAX_PAGING_ITERATIONS);
    }

    public CompletableFuture<QueryResults<GetNotification.Bundle, Long>> getNotificationsTimeline(long accountId, Long offsetMaybe, Integer limitMaybe, List<String> typesMaybe, List<String> excludeTypesMaybe) {
        int defaultLimit = 15;
        int maxLimit = 30;
        Set<String> types = new HashSet<>();
        if (typesMaybe != null) types.addAll(typesMaybe);
        Set<String> excludeTypes = new HashSet<>();
        if (excludeTypesMaybe != null) excludeTypes.addAll(excludeTypesMaybe);
        return queryWithPaging(
            (offset, limit) -> {
                SortedRangeFromOptions options = SortedRangeFromOptions.excludeStart().maxAmt(limit);
                CompletableFuture<List<List>> notificationsFuture = accountIdToNotificationsTimeline.selectAsync(Path.key(accountId).sortedMapRangeFrom(offset, options).all());
                return notificationsFuture.thenCompose(timelineIndexAndNotifications -> {
                    // create notifications and filter them
                    List<NotificationWithId> notificationWithIds = ApolloHelpers.createNotificationWithIds(timelineIndexAndNotifications);
                    List<NotificationWithId> filtered =
                            notificationWithIds.stream()
                                               .filter(nwid -> types.contains(ApolloHelpers.getTypeFromNotificationContent(nwid.notification.content)))
                                               .filter(nwid -> !excludeTypes.contains(ApolloHelpers.getTypeFromNotificationContent(nwid.notification.content)))
                                               .collect(Collectors.toList());
                    // get any accounts/statuses associated with the notifications
                    List<CompletableFuture<GetNotification.Bundle>> bundleFutures =
                            filtered.stream()
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
                                                    if (bundle != null) bundles.add(bundle);
                                                }
                                                Long lastId = null;
                                                List<SimpleEntry<String, String>> linkHeaderParams = null;
                                                if (notificationWithIds.size() > 0) {
                                                    NotificationWithId lastNotification = notificationWithIds.get(notificationWithIds.size()-1);
                                                    lastId = lastNotification.notificationId;
                                                    linkHeaderParams = Arrays.asList(new SimpleEntry<>("max_id", ApolloHelpers.serializeNotificationId(lastNotification.notificationId, lastNotification.notification.timestamp)));
                                                }
                                                return new QueryResults<>(bundles, notificationWithIds.size() < limit, lastId, linkHeaderParams);
                                            });
                });
            },
            offsetMaybe == null ? -1L : offsetMaybe,
            Math.min(limitMaybe == null ? defaultLimit : limitMaybe, maxLimit),
            MAX_PAGING_ITERATIONS
        );
    }

    public CompletableFuture<GetNotification.Bundle> getNotification(long requestAccountId, NotificationWithId notificationWithId) {
        // get account associated with the notification
        return this.getAccountWithId(ApolloHelpers.getAccountIdFromNotificationContent(notificationWithId.notification.content))
                   .thenCompose(accountWithId -> {
                       if (accountWithId == null) return CompletableFuture.completedFuture(null);
                       // determine if requester is currently muting this account's notifications
                       CompletableFuture<MuteAccountOptions> optionsFuture = accountIdToSuppressions.selectOneAsync(Path.key(requestAccountId, "muted", accountWithId.accountId));
                       return optionsFuture.thenCompose(muteAccountOptions -> {
                           if (muteAccountOptions != null && muteAccountOptions.muteNotifications) return CompletableFuture.completedFuture(null);
                           // get status associated with the notification
                           StatusPointer statusPointer = ApolloHelpers.getStatusPointerFromNotificationContent(notificationWithId.notification.content);
                           if (statusPointer == null) {
                               return CompletableFuture.completedFuture(new GetNotification.Bundle(notificationWithId, accountWithId, null));
                           } else {
                               QueryFilterOptions filterOptions = new QueryFilterOptions(FilterContext.Notifications, true);
                               return this.getStatus(requestAccountId, statusPointer, filterOptions)
                                          .thenApply(statusQueryResult -> {
                                              if (statusQueryResult == null) return null;
                                              return new GetNotification.Bundle(notificationWithId, accountWithId, statusQueryResult);
                                          });
                           }
                       });
                   });
    }

    public CompletableFuture<GetNotification.Bundle> getNotification(long accountId, long notificationId) {
        return accountIdToNotificationsTimeline.selectOneAsync(Path.key(accountId, notificationId))
                                               .thenCompose(notification -> {
                                                   if (notification == null) return null;
                                                   NotificationWithId notificationWithId = new NotificationWithId(notificationId, (Notification) notification);
                                                   return this.getNotification(accountId, notificationWithId);
                                               });
    }

    public CompletableFuture<Boolean> dismissNotification(long accountId, Long notificationIdMaybe) {
        DismissNotification dismissNotification = new DismissNotification(accountId);
        if (notificationIdMaybe != null) dismissNotification.setNotificationId(notificationIdMaybe);
        return dismissDepot.appendAsync(dismissNotification).thenApply(res -> true);
    }

    public CompletableFuture<Boolean> postPollVote(long accountId, StatusPointer pointer, Set<Integer> choices) {
        return pollVoteDepot.appendAsync(new PollVote(accountId, pointer, choices, System.currentTimeMillis())).thenApply(res -> true);
    }

    public CompletableFuture<List<AccountWithId>> getDirectory(boolean showAll, boolean sortByActive, Integer limitMaybe, Integer offsetMaybe) {
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
                if (count == offset + limit) break;
                // remove existing account id if necessary
                Integer existingIndex = accountIdToIndex.get(accountId);
                if (existingIndex != null) accountIds.set(existingIndex, null);
                else count += 1;
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
                          .thenCompose(filterId ->
                              accountIdToFilterIdToFilter.selectOneAsync(Path.key(filter.accountId, filterId))
                                  .thenApply(foundFilter -> new FilterWithId((long) filterId, (Filter) foundFilter)));
    }

    public CompletableFuture<List<FilterWithId>> getFilters(Long requestAccountId) {
        return accountIdToFilterIdToFilter.selectAsync(Path.key(requestAccountId).all())
                                          .thenApply(result -> ApolloHelpers.createFiltersWithIds((List) result));
    }

    public CompletableFuture<FilterWithId> getFilter(Long accountId, Long filterId) {
        return accountIdToFilterIdToFilter.selectOneAsync(Path.key(accountId, filterId))
                                          .thenApply(result -> {
                                              if (result == null) return null;
                                              return new FilterWithId(filterId, (Filter) result);
                                          });
    }

    public CompletableFuture<FilterWithId> putFilter(EditFilter edit) {
        return filterDepot.appendAsync(edit)
                          .thenCompose(res -> accountIdToFilterIdToFilter.selectOneAsync(Path.key(edit.accountId, edit.filterId)))
                          .thenApply(filter -> filter == null? null : new FilterWithId(edit.filterId, (Filter) filter));
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
        return followHashtagDepot.appendAsync(new FollowHashtag(accountId, hashtag, System.currentTimeMillis())).thenApply(res -> true);
    }

    public CompletableFuture<Boolean> postRemoveFollowHashtag(long accountId, String hashtag) {
        return followHashtagDepot.appendAsync(new RemoveFollowHashtag(accountId, hashtag, System.currentTimeMillis())).thenApply(res -> true);
    }

    public CompletableFuture<Boolean> isFollowingHashtag(long accountId, String hashtag) {
        return hashtagToFollowers.selectOneAsync(Path.key(hashtag).view(Ops.CONTAINS, accountId));
    }
    

}

   