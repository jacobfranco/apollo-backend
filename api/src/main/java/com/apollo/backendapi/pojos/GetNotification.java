package com.apollo.backendapi.pojos;

import com.apollo.backend.ApolloHelpers;
import com.apollo.backend.data.*;

import java.time.Instant;
import java.time.format.DateTimeFormatter;

public class GetNotification {
    public String id;
    public String type;
    public String created_at;
    public GetAccount account;
    public GetStatus status;
    public GetReport report;

    public static class Bundle {
        NotificationWithId notificationWithId;
        AccountWithId accountWithId;
        StatusQueryResult statusQueryResult;

        public Bundle(NotificationWithId notificationWithId, AccountWithId accountWithId, StatusQueryResult statusQueryResult) {
            this.notificationWithId = notificationWithId;
            this.accountWithId = accountWithId;
            this.statusQueryResult = statusQueryResult;
        }
    }

    public GetNotification() { }

    public GetNotification(Bundle bundle) {
        this.id = ApolloHelpers.serializeNotificationId(bundle.notificationWithId.notificationId, bundle.notificationWithId.notification.timestamp);
        this.type = ApolloHelpers.getTypeFromNotificationContent(bundle.notificationWithId.notification.content);
        this.created_at = DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(bundle.notificationWithId.notification.timestamp));
        this.account = new GetAccount(bundle.accountWithId);
        if (bundle.statusQueryResult != null) this.status = new GetStatus(bundle.statusQueryResult);
    }
}
