package com.apollo.backendapi.pojos;

import com.apollo.backend.ApolloConfig;
import com.apollo.backend.ApolloHelpers;
import com.apollo.backend.data.*;
import com.apollo.backendapi.ApolloApiConfig;
import com.apollo.backendapi.ApolloApiHelpers;
import org.apache.commons.text.StringEscapeUtils;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

public class GetAccount {
    public String id;
    public String username;
    public String acct;
    public String display_name;
    public String note;
    public boolean locked;
    public boolean bot;
    public boolean discoverable;
    public boolean group;
    public String url;
    public String avatar;
    public String avatar_static;
    public String header;
    public String header_static;

    public static class Field {
        public String name;
        public String value;
        public String verified_at;

        public Field() {
        }

        public Field(String name, String value) {
            this.name = name;
            this.value = value;
        }
    }

    public List<Field> fields;

    public static class Source {
        public String note = "";
        public List<Field> fields;
        public String privacy = "public";
        public boolean sensitive = false;
        public String language = "";
        public int follow_requests_count = 0;
    }

    public Source source;
    public String created_at;
    public String last_status_at;
    public int statuses_count;
    public int followers_count;
    public int following_count;

    public GetAccount() {
    }

    public GetAccount(AccountWithId accountWithId) {
        this(accountWithId.accountId, accountWithId.account, accountWithId.metadata);
    }

    // TODO: Change unncessary fields
    public GetAccount(long accountId, Account account, AccountMetadata metadata) {
        this.id = ApolloHelpers.serializeAccountId(accountId);
        this.username = account.name;
        this.acct = account.name;
        this.display_name = account.isSetDisplayName() ? StringEscapeUtils.escapeHtml4(account.displayName) : "";
        this.note = account.isSetBio() ? StringEscapeUtils.escapeHtml4(account.bio) : "";
        this.locked = account.locked;
        this.bot = account.bot;
        this.discoverable = account.discoverable;

        this.url = ApolloConfig.FRONTEND_URL + "/@" + account.name; // TODO: Maybe change

        if (account.isSetFields())
            this.fields = account.fields.stream().map(field -> new Field(StringEscapeUtils.escapeHtml4(field.key),
                    StringEscapeUtils.escapeHtml4(field.value))).collect(Collectors.toList());
        else
            this.fields = new ArrayList<>();

        this.source = new Source();
        this.source.note = this.note;
        this.source.fields = this.fields;

        if (ApolloApiConfig.S3_OPTIONS != null) {
            if (account.isSetAvatar() && !account.avatar.attachment.path.isEmpty()) {
                if (ApolloApiHelpers.isValidURL(account.avatar.attachment.path))
                    this.avatar = account.avatar.attachment.path;
                else
                    this.avatar = String.format("%s/%s", ApolloApiConfig.S3_OPTIONS.url,
                            account.avatar.attachment.path);

                this.avatar_static = this.avatar;
            }
            if (account.isSetHeader() && !account.header.attachment.path.isEmpty()) {
                if (ApolloApiHelpers.isValidURL(account.header.attachment.path))
                    this.header = account.header.attachment.path;
                else
                    this.header = String.format("%s/%s", ApolloApiConfig.S3_OPTIONS.url,
                            account.header.attachment.path);

                this.header_static = this.header;
            }
        } else {
            if (account.isSetAvatar() && !account.avatar.attachment.path.isEmpty()) {
                if (ApolloApiHelpers.isValidURL(account.avatar.attachment.path))
                    this.avatar = account.avatar.attachment.path;
                else
                    this.avatar = String.format("%s/%s/%s", ApolloConfig.API_URL,
                            ApolloApiConfig.STATIC_FILE_URL_PATH_NAME, account.avatar.attachment.path);

                this.avatar_static = this.avatar;
            }
            if (account.isSetHeader() && !account.header.attachment.path.isEmpty()) {
                if (ApolloApiHelpers.isValidURL(account.header.attachment.path))
                    this.header = account.header.attachment.path;
                else
                    this.header = String.format("%s/%s/%s", ApolloConfig.API_URL,
                            ApolloApiConfig.STATIC_FILE_URL_PATH_NAME, account.header.attachment.path);

                this.header_static = this.header;
            }
        }

        /*
         * // default images
         * if (this.header == null) {
         * this.header = ApolloConfig.API_URL + "/missing_header.png";
         * this.header_static = ApolloConfig.API_URL + "/missing_header.png";
         * }
         * if (this.avatar == null) {
         * this.avatar = ApolloConfig.API_URL + "/missing_avatar.png";
         * this.avatar_static = ApolloConfig.API_URL + "/missing_avatar.png";
         * }
         */

        this.created_at = DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(account.timestamp));

        if (metadata != null) {
            if (metadata.isSetLastStatusTimestamp())
                this.last_status_at = DateTimeFormatter.ISO_INSTANT
                        .format(Instant.ofEpochMilli(metadata.lastStatusTimestamp));
            this.statuses_count = metadata.statusCount;
            this.followers_count = metadata.followerCount;
            this.following_count = metadata.followeeCount;
        }
    }
}