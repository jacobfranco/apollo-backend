package com.apollo.backend.api;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import com.apollo.backend.data.*;
import com.apollo.backend.modules.*;
import com.rpl.rama.*;
import com.rpl.rama.cluster.ClusterManagerBase;

public class ApolloApiManager {

    public static final String CORE_MODULE_NAME = Core.class.getName();

    // Depots
    private final Depot accountDepot;

    // PStates
    private final PState nameToUser;

    // Topologies
    private final QueryTopologyClient<List<AccountWithId>> getAccountsFromAccountIds;


    public ApolloApiManager(ClusterManagerBase cluster) {

        // Depots
        accountDepot = cluster.clusterDepot(CORE_MODULE_NAME, "*accountDepot");

        // PStates
        nameToUser = cluster.clusterPState(CORE_MODULE_NAME, "$$nameToUser");

        // Topologies
        getAccountsFromAccountIds = cluster.clusterQuery(CORE_MODULE_NAME, "getAccountsFromAccountIds");

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
        // Creating a new Account object with the necessary parameters
    Account newAccount = new Account(params, pwdHash, uuid, keys.publicKey, System.currentTimeMillis());

    // Using accountDepot to append the new account asynchronously
    return accountDepot.appendAsync(newAccount)
                       .thenCompose(res -> this.getAccountUUID(params.username))
                       .thenApply(accountUUID -> accountUUID.equals(uuid));
}

public CompletableFuture<String> getAccountUUID(String username) {
    return nameToUser.selectOneAsync(Path.key(username, "uuid"));
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

}
    
