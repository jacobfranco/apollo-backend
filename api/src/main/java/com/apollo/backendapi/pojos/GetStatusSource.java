package com.apollo.backendapi.pojos;

import com.apollo.backend.ApolloHelpers;
import com.apollo.backend.data.*;
import com.apollo.backendapi.ApolloApiHelpers;

public class GetStatusSource {
    public String id;
    public String text;
    public String spoiler_text;

    public GetStatusSource() { }

    public GetStatusSource(StatusQueryResult statusQueryResult) {
        this.id = ApolloHelpers.serializeStatusPointer(new StatusPointer(statusQueryResult.result.status.author.accountId, statusQueryResult.result.statusId));
        this.text = ApolloApiHelpers.getStatusResultContentText(statusQueryResult.result.status.content);
        this.spoiler_text = "";
    }
}
