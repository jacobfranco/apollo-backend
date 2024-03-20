package com.apollo.backendapi;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import com.apollo.backend.data.*;
import com.apollo.backend.modules.*;
import com.apollo.backendapi.pojos.PostAccount;
import com.rpl.rama.*;
import com.rpl.rama.cluster.ClusterManagerBase;

public class ApolloApiManager {
    // Modules
    public static final String CORE_MODULE_NAME = Core.class.getName();
    public static final String RELATIONSHIPS_MODULE_NAME = Relationships.class.getName();

    // Depots
    private final Depot authCodeDepot;

    // PStates
    private final PState nameToUser;

    // Query Topologies
    private final QueryTopologyClient<List<AccountWithId>> getAccountsFromAccountIds;

    public ApolloApiManager(ClusterManagerBase cluster) {

        // Depots
        authCodeDepot = cluster.clusterDepot(RELATIONSHIPS_MODULE_NAME, "*authCodeDepot");

        // PStates
        nameToUser = cluster.clusterPState(CORE_MODULE_NAME, "$$nameToUser");

        // Query Topologies
        getAccountsFromAccountIds = cluster.clusterQuery(CORE_MODULE_NAME, "getAccountsFromAccountIds");

        

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
        String pwdHash = MastodonApiHelpers.encodePassword(params.password);
        String uuid = UUID.randomUUID().toString();
        final MastodonWebHelpers.SigningKeyPair keys;
        try {
            keys = MastodonWebHelpers.generateKeys();
        } catch (NoSuchProviderException | NoSuchAlgorithmException | IOException e) {
            return CompletableFuture.completedFuture(false);
        }
        return accountDepot.appendAsync(new Account(params.username, params.email, pwdHash, params.locale, uuid, keys.publicKey, AccountContent.local(new LocalAccount(keys.privateKey)), System.currentTimeMillis()))
                           .thenCompose(res -> this.getAccountUUID(params.username))
                           .thenApply(accountUUID -> accountUUID.equals(uuid));
    }


}

   