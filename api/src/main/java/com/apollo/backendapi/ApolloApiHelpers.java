package com.apollo.backendapi;

import com.apollo.backend.*;
import com.apollo.backend.data.*;
import com.apollo.backendapi.pojos.*;

import java.io.*;
import java.net.*;
import java.security.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.AbstractMap.SimpleEntry;

import org.bouncycastle.util.encoders.Hex;
import org.springframework.security.crypto.bcrypt.*;
import org.springframework.security.crypto.password.*;
import org.springframework.web.server.*;
import org.springframework.http.*;
import org.springframework.web.util.UriComponentsBuilder;

import software.amazon.awssdk.auth.credentials.EnvironmentVariableCredentialsProvider;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.*;


public class ApolloApiHelpers {

    private static final DelegatingPasswordEncoder PASSWORD_ENCODER;

    static {
        HashMap<String, PasswordEncoder> encoders = new HashMap<>();
        encoders.put("bcrypt", new BCryptPasswordEncoder());
        PASSWORD_ENCODER = new DelegatingPasswordEncoder("bcrypt", encoders);
    }

    public static String encodePassword(String password) {
        return PASSWORD_ENCODER.encode(password);
    }

    public static boolean matchesPassword(String password, String passwordHash) {
        return PASSWORD_ENCODER.matches(password, passwordHash);
    }

     public static String randomString(int size) throws NoSuchAlgorithmException {
        byte[] bytes = new byte[size];
        SecureRandom.getInstanceStrong().nextBytes(bytes);
        return Hex.toHexString(bytes);
    }

     private static S3AsyncClient S3_CLIENT = null;
    public static void initS3Client() {
        S3_CLIENT = S3AsyncClient.builder().credentialsProvider(EnvironmentVariableCredentialsProvider.create()).build();
    }

    public static CompletableFuture<PutObjectResponse> uploadToS3(String bucketName, String key, File file) {
        PutObjectRequest objectRequest = PutObjectRequest.builder().bucket(bucketName).key(key).build();
        return S3_CLIENT.putObject(objectRequest, AsyncRequestBody.fromFile(file));
    }

     public static boolean isValidURL(String url) {
        try {
            new URL(url).toURI();
            return true;
        } catch (MalformedURLException | URISyntaxException e) {
            return false;
        }
    }

    public static String createFilterContext(FilterContext context) {
        switch (context) {
            case Home: return "home";
            case Notifications: return "notifications";
            case Public: return "public";
            case Thread: return "thread";
            case Account: return "account";
        }
        throw new RuntimeException("Invalid filter context");
    }

    public static String createFilterAction(FilterAction action) {
        switch (action) {
            case Warn: return "warn";
            case Hide: return "hide";
        }
        throw new RuntimeException("Invalid filter action");
    }

    public static StatusVisibility createStatusVisibility(String visibilityStr) {
        if ("public".equals(visibilityStr)) return StatusVisibility.Public;
        else if ("unlisted".equals(visibilityStr)) return StatusVisibility.Unlisted;
        else if ("private".equals(visibilityStr)) return StatusVisibility.Private;
        else if ("direct".equals(visibilityStr)) return StatusVisibility.Direct;
        else throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
    }

    public static String createStatusVisibility(StatusVisibility visibility) {
        switch (visibility) {
            case Public: return "public";
            case Unlisted: return "unlisted";
            case Private: return "private";
            case Direct: return "direct";
        }
        throw new RuntimeException("Invalid visibility");
    }

    public static <T, O> void setLinkHeader(ServerWebExchange exchange, ApolloApiManager.QueryResults<T, O> queryResults) {
        if (queryResults.linkHeaderParams != null && !queryResults.reachedEnd) {
            UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(ApolloConfig.API_URL);
            builder.path(exchange.getRequest().getPath().pathWithinApplication().value());
            // collect the existing query params
            for (Map.Entry<String, List<String>> entry : exchange.getRequest().getQueryParams().entrySet()) {
                builder.queryParam(entry.getKey(), entry.getValue());
            }
            // collect the new params (these will override the existing ones)
            for (SimpleEntry<String, String> entry : queryResults.linkHeaderParams) {
                builder.replaceQueryParam(entry.getKey(), entry.getValue());
            }
            // set the header
            exchange.getResponse().getHeaders().add("Link", String.format("<%s>; rel=\"next\"", builder.toUriString()));
        }
    }

    public static List<GetAccount> createGetAccounts(List<AccountWithId> results) {
        List<GetAccount> getAccounts = new ArrayList<>();
        for (AccountWithId result : results) getAccounts.add(new GetAccount(result));
        return getAccounts;
    }

    public static List<GetConversation> createGetConversations(List<Conversation> convos) {
        List<GetConversation> getConversations = new ArrayList<>();
        for (Conversation convo : convos) getConversations.add(new GetConversation(convo));
        return getConversations;
    }

    public static GetTag createGetTag(String hashtag, ItemStats stats, boolean isFollowing) {
        GetTag tag = new GetTag(hashtag);
        Map<Long, DayBucket> buckets = stats.dayBuckets;
        buckets.forEach((Long day, DayBucket b) -> {
            tag.history.add(new GetTag.HistoryItem(day, b.uses, b.accounts));
        });
        tag.following = isFollowing;
        return tag;
    }

    public static List<GetTag> createGetTags(Map<String, ItemStats> hashtagToStats) {
        List<GetTag> getTags = new ArrayList<>();
        hashtagToStats.forEach((String hashtag, ItemStats stats) -> {
            getTags.add(createGetTag(hashtag, stats, false));
        });
        return getTags;
    }

    public static List<GetTag> createGetTags(List<SimpleEntry<String, ItemStats>> hashtagToStats) {
        List<GetTag> getTags = new ArrayList<>();
        for (SimpleEntry<String, ItemStats> entry : hashtagToStats) {
            getTags.add(createGetTag(entry.getKey(), entry.getValue(), false));
        }
        return getTags;
    }

    public static List<GetStatus> createGetStatuses(StatusQueryResults statusQueryResults) {
        List<GetStatus> getStatuses = new ArrayList<>();
        for (StatusResultWithId result : statusQueryResults.results) getStatuses.add(new GetStatus(result, statusQueryResults.mentions));
        return getStatuses;
    }

    public static List<GetStatus> createGetStatuses(List<StatusQueryResult> statusQueryResults) {
        List<GetStatus> getStatuses = new ArrayList<>();
        for (StatusQueryResult statusQueryResult : statusQueryResults) getStatuses.add(new GetStatus(statusQueryResult.result, statusQueryResult.mentions));
        return getStatuses;
    }

    public static String getStatusResultContentText(StatusResultContent content) {
        if (content.isSetNormal()) return content.getNormal().text;
        else if (content.isSetReply()) return content.getReply().text;
        else if (content.isSetBoost()) return getStatusResultContentText(content.getBoost().status.content);
        return "";
    }

    public static void setStatusLinkHeader(ServerWebExchange exchange, StatusQueryResults statusQueryResults) {
        if (statusQueryResults.isSetLastStatusPointer() && !statusQueryResults.reachedEnd) {
            UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(ApolloConfig.API_URL);
            builder.path(exchange.getRequest().getPath().pathWithinApplication().value());
            // collect the existing query params
            for (Map.Entry<String, List<String>> entry : exchange.getRequest().getQueryParams().entrySet()) {
                builder.queryParam(entry.getKey(), entry.getValue());
            }
            // collect the new params (these will override the existing ones)
            builder.replaceQueryParam("max_id", ApolloHelpers.serializeStatusPointer(statusQueryResults.lastStatusPointer));
            // set the header
            exchange.getResponse().getHeaders().add("Link", String.format("<%s>; rel=\"next\"", builder.toUriString()));
        }
    }

}