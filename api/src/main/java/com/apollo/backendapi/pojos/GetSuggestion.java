package com.apollo.backendapi.pojos;

import com.apollo.backend.data.AccountWithId;

public class GetSuggestion {
    public String source;
    public GetAccount account;

    public GetSuggestion() { }

    public GetSuggestion(String source, AccountWithId accountWithId) {
        this.source = source;
        this.account = new GetAccount(accountWithId);
    }
}
