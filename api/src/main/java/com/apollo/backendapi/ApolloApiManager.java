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

public class ApolloApiManager {

    private static final int MAX_PAGING_ITERATIONS = 10;
    private static final int MAX_LIMIT = 40;
    private static final int DEFAULT_LIMIT = 20;

    // Modules
    public static final String CORE_MODULE_NAME = Core.class.getName();
    public static final String RELATIONSHIPS_MODULE_NAME = Relationships.class.getName();
    public static final String HASHTAGS_MODULE_NAME = TrendsAndHashtags.class.getName();

    // Core Depots
    private final Depot accountDepot;
    private final Depot accountEditDepot;
    private final Depot statusDepot;
    private final Depot scheduledStatusDepot;
    private final Depot conversationDepot;
    

     // Relationships Depots
    private final Depot authCodeDepot;
    private final Depot followAndBlockAccountDepot;
    private final Depot muteAccountDepot;
    private final Depot featureAccountDepot;
    

    // Core PStates
    private final PState nameToUser;
    private final PState pinnerToStatusIds;
    private final PState uuidToAttachment;
    private final PState postUUIDToStatusId;
    private final PState accountIdToScheduledStatuses;
    private final PState accountIdToConvoIds;

    // Relationship PStates
    private final PState authCodeToAccountId;
    private final PState followerToFolloweesById;
    private final PState followeeToFollowersById;
    private final PState accountIdToFollowRequests;
    private final PState accountIdToFollowRequestsById;

    // Hashtag/Trends PStates
    private final PState hashtagTrends;
    private final PState statusTrends;

    // Core Queries
    private final QueryTopologyClient<List<AccountWithId>> getAccountsFromAccountIds;
    private final QueryTopologyClient<StatusQueryResults> getAccountTimeline;
    private final QueryTopologyClient<StatusQueryResults> getStatusesFromPointers;
    private final QueryTopologyClient<Conversation> getConversation;
    private final QueryTopologyClient<List<Conversation>> getConversationTimeline;
    

    // Relationship Queries
    private final QueryTopologyClient<AccountRelationshipQueryResult> getAccountRelationship;

    // Hashtag Queries
    private final QueryTopologyClient<Map<String, ItemStats>> batchHashtagStats;

    public ApolloApiManager(ClusterManagerBase cluster) {


        // Core Depots
        accountDepot = cluster.clusterDepot(CORE_MODULE_NAME, "*accountDepot");
        accountEditDepot = cluster.clusterDepot(CORE_MODULE_NAME, "*accountEditDepot");
        statusDepot = cluster.clusterDepot(CORE_MODULE_NAME, "*statusDepot");
        scheduledStatusDepot = cluster.clusterDepot(CORE_MODULE_NAME, "*scheduledStatusDepot");
        conversationDepot = cluster.clusterDepot(CORE_MODULE_NAME, "*conversationDepot");
        
        // Relationships Depots
        authCodeDepot = cluster.clusterDepot(RELATIONSHIPS_MODULE_NAME, "*authCodeDepot");
        followAndBlockAccountDepot = cluster.clusterDepot(RELATIONSHIPS_MODULE_NAME, "*followAndBlockAccountDepot");
        muteAccountDepot = cluster.clusterDepot(RELATIONSHIPS_MODULE_NAME, "*muteAccountDepot");
        featureAccountDepot = cluster.clusterDepot(RELATIONSHIPS_MODULE_NAME, "*featureAccountDepot");

        // Core PStates
        nameToUser = cluster.clusterPState(CORE_MODULE_NAME, "$$nameToUser");
        pinnerToStatusIds = cluster.clusterPState(CORE_MODULE_NAME, "$$pinnerToStatusIds");
        accountIdToScheduledStatuses = cluster.clusterPState(CORE_MODULE_NAME, "$$accountIdToScheduledStatuses");
        postUUIDToStatusId = cluster.clusterPState(CORE_MODULE_NAME, "$$postUUIDToStatusId");
        uuidToAttachment = cluster.clusterPState(CORE_MODULE_NAME, "$$uuidToAttachment");
        accountIdToConvoIds = cluster.clusterPState(CORE_MODULE_NAME, "$$accountIdToConvoIds");

        // Relationship PStates
        authCodeToAccountId = cluster.clusterPState(RELATIONSHIPS_MODULE_NAME, "$$authCodeToAccountId");
        followerToFolloweesById = cluster.clusterPState(RELATIONSHIPS_MODULE_NAME, "$$followerToFolloweesById");
        followeeToFollowersById = cluster.clusterPState(RELATIONSHIPS_MODULE_NAME, "$$followeeToFollowersById");
        accountIdToFollowRequests = cluster.clusterPState(RELATIONSHIPS_MODULE_NAME, "$$accountIdToFollowRequests");
        accountIdToFollowRequestsById = cluster.clusterPState(RELATIONSHIPS_MODULE_NAME, "$$accountIdToFollowRequestsById");

        // Hashtag/Trends PStates
        hashtagTrends = cluster.clusterPState(HASHTAGS_MODULE_NAME, "$$hashtagTrends");
        statusTrends = cluster.clusterPState(HASHTAGS_MODULE_NAME, "$$statusTrends");
        
        // Core Queries
        getAccountsFromAccountIds = cluster.clusterQuery(CORE_MODULE_NAME, "getAccountsFromAccountIds");
        getAccountTimeline = cluster.clusterQuery(CORE_MODULE_NAME, "getAccountTimeline");
        getStatusesFromPointers = cluster.clusterQuery(CORE_MODULE_NAME, "getStatusesFromPointers");
        getConversation = cluster.clusterQuery(CORE_MODULE_NAME, "getConversation");
        getConversationTimeline = cluster.clusterQuery(CORE_MODULE_NAME, "getConversationTimeline");

        // Relationships Queries
        getAccountRelationship = cluster.clusterQuery(RELATIONSHIPS_MODULE_NAME, "getAccountRelationship");

        // Hashtag Queries
        batchHashtagStats = cluster.clusterQuery(HASHTAGS_MODULE_NAME, "batchHashtagStats");

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

    public CompletableFuture<StatusQueryResult> postStatus(long accountId, PostStatus params, String remoteUrl) {
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





}

   