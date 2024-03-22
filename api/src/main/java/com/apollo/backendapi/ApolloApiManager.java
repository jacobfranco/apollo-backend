package com.apollo.backendapi;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import com.apollo.backend.*;
import com.apollo.backend.data.*;
import com.apollo.backend.modules.*;
import com.apollo.backendapi.pojos.PostAccount;
import com.rpl.rama.*;
import com.rpl.rama.cluster.ClusterManagerBase;

public class ApolloApiManager {

    // Modules
    public static final String CORE_MODULE_NAME = Core.class.getName();
    public static final String RELATIONSHIPS_MODULE_NAME = Relationships.class.getName();

    // Core Depots
    private final Depot accountDepot;

     // Relationships Depots
    private final Depot authCodeDepot;

    // Core PStates
    private final PState nameToUser;

    // Core Query Topologies
    private final QueryTopologyClient<List<AccountWithId>> getAccountsFromAccountIds;
    private final QueryTopologyClient<StatusQueryResults> getAccountTimeline;

    public ApolloApiManager(ClusterManagerBase cluster) {

        // Core Depots
        accountDepot = cluster.clusterDepot(CORE_MODULE_NAME, "*accountDepot");

        // Relationships Depots
        authCodeDepot = cluster.clusterDepot(RELATIONSHIPS_MODULE_NAME, "*authCodeDepot");

        // Core PStates
        nameToUser = cluster.clusterPState(CORE_MODULE_NAME, "$$nameToUser");

        // Core Query Topologies
        getAccountsFromAccountIds = cluster.clusterQuery(CORE_MODULE_NAME, "getAccountsFromAccountIds");
        getAccountTimeline = cluster.clusterQuery(CORE_MODULE_NAME, "getAccountTimeline");

        

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


}

   