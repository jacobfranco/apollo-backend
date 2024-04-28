package com.apollo.backendapi.pojos;

import com.apollo.backend.data.Conversation;
import com.apollo.backendapi.ApolloApiHelpers;

import java.util.List;

public class GetConversation {
    public String id;
    public boolean unread;
    public List<GetAccount> accounts;
    public GetStatus last_status;

    public GetConversation() { }

    public GetConversation(Conversation convo) {
        this.id = convo.conversationId+"";
        this.unread = convo.unread;
        this.accounts = ApolloApiHelpers.createGetAccounts(convo.accounts);
        if (convo.lastStatus != null) this.last_status = new GetStatus(convo.lastStatus);
    }
}
