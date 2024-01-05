package com.apollo.backend.data;

public class AccountWithId {
    private long accountId;
    private Account account;
    private AccountMetadata metadata;

    public AccountWithId() {
    }

    public AccountWithId(long accountId, Account account, AccountMetadata metadata) {
        this.accountId = accountId;
        this.account = account;
        this.metadata = metadata;
    }
}
