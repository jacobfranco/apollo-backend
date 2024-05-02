package com.apollo.backendapi;

import com.apollo.backend.*;
import com.apollo.backend.data.*;
import com.apollo.backendapi.pojos.*;

import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.*;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.*;
import org.springframework.http.codec.multipart.Part;
import org.springframework.core.io.buffer.DataBuffer;

import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;

import java.util.*;
import java.security.NoSuchAlgorithmException;
import java.util.stream.Collectors;
import java.nio.charset.Charset;

@RestController
@CrossOrigin(exposedHeaders = { "Link" })
public class ApolloApiController {

    public static ApolloApiManager manager;

    /*
     * Helper Functions
     * ======================================
     * - loginWithAccount
     * - getMandatoryAccountId
     * - validateStatus
     * ======================================
     */

    // Login Function
    private Mono<GetToken> loginWithAccount(WebSession session, String scope, AccountWithId accountWithId) {
        // Update Session
        session.getAttributes().put("accountId", accountWithId.accountId);
        session.getAttributes().put("accountName", accountWithId.account.displayName);
        // Store the session id in the backend and return token
        return Mono.fromFuture(manager.postAuthCode(accountWithId.accountId, session.getId()))
                .map(res -> new GetToken(session.getId(), scope));
    }

    // Extract the mandatory account ID from the session, throw an exception if not
    // present
    private static long getMandatoryAccountId(WebSession session) {
        // Attempt to retrieve the account ID from session attributes
        Long requestAccountId = (Long) session.getAttributes().get("accountId");
        // Throw if account ID is missing
        if (requestAccountId == null)
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        // Return the found account ID
        return requestAccountId;
    }

    private void validateStatus(String content, String spoilerText, String language, PostStatus.Poll poll) {
        if (content.length() > ApolloApiConfig.MAX_STATUS_LENGTH)
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Status too long");
        if (spoilerText != null && spoilerText.length() > ApolloApiConfig.MAX_STATUS_LENGTH)
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Spoiler text too long");
        if (language != null && language.length() > ApolloApiConfig.MAX_STATUS_LENGTH)
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Language string too long");
        if (poll != null && poll.options != null) {
            if (poll.options.size() > 4)
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Too many poll choices");
            for (String option : poll.options) {
                if (option.length() > ApolloApiConfig.MAX_POLL_CHOICE_LENGTH)
                    throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Poll choice too long");
            }
        }
    }

    /*
     * Apps + OAuth Actions Endpoints
     * ======================================
     * - POST /api/apps
     * - POST /oauth/token
     * - POST /oauth/revoke
     * ======================================
     */

    // Define a controller method to handle POST requests for application
    // registration with JSON payload
    @PostMapping(value = "/api/apps", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Mono<GetApplication> postApplication(@RequestBody(required = true) PostApplication params)
            throws NoSuchAlgorithmException {
        // Initialize a new GetApplication instance to hold the application data
        GetApplication app = new GetApplication();
        app.redirect_uri = params.redirect_uris;
        // Generate a new client secret for the application
        app.client_secret = "secret_" + ApolloApiHelpers.randomString(16);
        // Currently returns the application without storing it, needs future
        // implementation for storage
        return Mono.just(app);
    }

    // Define a controller method to handle POST requests for application
    // registration with form URL encoded payload
    @PostMapping(value = "/api/apps", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public Mono<GetApplication> postApplication(ServerWebExchange exchange) {
        // Parse form data and delegate to the JSON payload handling method
        return exchange.getFormData()
                .flatMap(formParams -> {
                    PostApplication params = ApolloApiFormParser.parseParams(formParams, new PostApplication());
                    try {
                        return this.postApplication(params);
                    } catch (NoSuchAlgorithmException e) {
                        // Wrap the NoSuchAlgorithmException in a RuntimeException
                        throw new RuntimeException(e);
                    }
                });
    }

    // Define a controller method to handle POST requests for OAuth token generation
    // with JSON payload
    @PostMapping(value = "/oauth/token", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Mono<GetToken> postOauthToken(WebSession session, @RequestBody(required = true) PostToken params) {
        // Handle the "password" grant type
        if ("password".equals(params.grant_type)) {
            return Mono.fromFuture(manager.getAccountId(params.username))
                    .switchIfEmpty(
                            Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Username not found")))
                    // Attempt to retrieve the account using the account ID
                    .flatMap(accountId -> Mono.fromFuture(manager.getAccountWithId(accountId)))
                    .switchIfEmpty(
                            Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Username not found")))
                    // Validate the password and log in the user
                    .flatMap(accountWithId -> {
                        if (!ApolloApiHelpers.matchesPassword(params.password, accountWithId.account.pwdHash)) {
                            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Password does not match");
                        }
                        return this.loginWithAccount(session, params.scope, accountWithId);
                    });
        } else if ("client_credentials".equals(params.grant_type)) {
            // Handle the "client_credentials" grant type
            return Mono.just(new GetToken(session.getId(), params.scope));
        } else {
            // Handle unsupported grant types
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
    }

    // Define a controller method to handle POST requests for OAuth token generation
    // with form URL encoded payload
    @PostMapping(value = "/oauth/token", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public Mono<GetToken> postOauthToken(WebSession session, ServerWebExchange exchange) {
        // Parse form data and delegate to the JSON payload handling method
        return exchange.getFormData()
                .flatMap(formParams -> {
                    PostToken params = ApolloApiFormParser.parseParams(formParams, new PostToken());
                    return this.postOauthToken(session, params);
                });
    }

    // TODO: See if this still works
    // Define a POST endpoint for revoking OAuth tokens with JSON payload
    @PostMapping(value = "/oauth/revoke", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Mono<Object> postRevokeOauthToken(@RequestBody(required = true) PostRevokeToken params) {
        // Invoke the manager to remove the authorization code using the token provided
        // and return an empty map as the response
        return Mono.fromFuture(manager.postRemoveAuthCode(params.token)).map(res -> new HashMap<String, Object>());
    }

    // Overloaded POST endpoint for revoking OAuth tokens using form-urlencoded
    // payload
    @PostMapping(value = "/oauth/revoke", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public Mono<Object> postRevokeOauthToken(ServerWebExchange exchange) {
        // Extract form data from the request
        return exchange.getFormData()
                .flatMap(formParams -> {
                    // Parse form parameters into a PostRevokeToken object
                    PostRevokeToken params = ApolloApiFormParser.parseParams(formParams, new PostRevokeToken());
                    // Delegate to the other postRevokeOauthToken method for processing
                    return this.postRevokeOauthToken(params);
                });
    }

    /*
     * Accounts + Auth Actions Endpoints
     * ======================================
     * - POST /api/accounts
     * - GET /api/accounts/verify_credentials
     * - GET /api/accounts/{id}
     * ======================================
     */

    // TODO: Added Object return type - make sure not broken now
    // Define a POST endpoint for creating new accounts
    @PostMapping("/api/accounts")
    public Mono<Object> postAccount(WebSession session, ServerHttpResponse response,
            @RequestBody(required = true) PostAccount params) {
        // Validate the username contains only alphanumeric characters
        if (!params.username.matches("[a-zA-Z0-9]*")) {
            response.setStatusCode(HttpStatus.UNPROCESSABLE_ENTITY);
            return Mono.just(new GetErrorDetails("Username must contain only a-z or numbers",
                    new HashMap<String, GetErrorDetails.Error>() {
                        {
                            put("username", new GetErrorDetails.Error("ERR_INVALID",
                                    "Username must contain only a-z or numbers"));
                        }
                    }));
        }
        // Check if the username length exceeds the maximum allowed length
        else if (params.username.length() > ApolloApiConfig.MAX_USERNAME_LENGTH) {
            response.setStatusCode(HttpStatus.UNPROCESSABLE_ENTITY);
            return Mono.just(new GetErrorDetails("Username too long", new HashMap<String, GetErrorDetails.Error>() {
                {
                    put("agreement", new GetErrorDetails.Error("ERR_INVALID",
                            "Username cannot be greater than " + ApolloApiConfig.MAX_USERNAME_LENGTH + " characters"));
                }
            }));
        }
        // Ensure the user agreement is accepted
        else if (!params.agreement) {
            response.setStatusCode(HttpStatus.UNPROCESSABLE_ENTITY);
            return Mono.just(new GetErrorDetails("The agreement has not been accepted",
                    new HashMap<String, GetErrorDetails.Error>() {
                        {
                            put("agreement",
                                    new GetErrorDetails.Error("ERR_ACCEPTED", "The agreement has not been accepted"));
                        }
                    }));
        }
        // Attempt to create the account with provided parameters
        return Mono.fromFuture(manager.postAccount(params))
                .flatMap(success -> {
                    if (success) {
                        // On success, retrieve the newly created account's ID
                        return Mono.fromFuture(manager.getAccountId(params.username))
                                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                                // Retrieve the complete account information using the ID
                                .flatMap(accountId -> Mono.fromFuture(manager.getAccountWithId(accountId)))
                                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                                // Log in the user and update the session with the account information
                                .flatMap(accountWithId -> this.loginWithAccount(session, "read write follow push",
                                        accountWithId));
                    } else {
                        // If the account creation fails due to the username being already in use
                        response.setStatusCode(HttpStatus.UNPROCESSABLE_ENTITY);
                        return Mono.just(
                                new GetErrorDetails("Validation failed", new HashMap<String, GetErrorDetails.Error>() {
                                    {
                                        put("username",
                                                new GetErrorDetails.Error("ERR_TAKEN", "Username already in use"));
                                    }
                                }));
                    }
                });
    }

    // Define a route for a GET request to verify account credentials
    @GetMapping("/api/accounts/verify_credentials")
    public Mono<GetAccount> getAccountVerifyCredentials(WebSession session) {
        // Extract the mandatory account ID from the session
        long requestAccountId = getMandatoryAccountId(session);
        // Retrieve the account information asynchronously and convert it into a
        // GetAccount response
        return Mono.fromFuture(manager.getAccountWithId(requestAccountId))
                // If the account is not found, return an HTTP 404 error
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                // Convert the account information into the GetAccount DTO format
                .map(GetAccount::new);
    }

    // Map a GET request to retrieve a specific account by its ID
    @GetMapping("/api/accounts/{id}")
    public Mono<GetAccount> getAccount(@PathVariable("id") String accountId) {
        // Convert the account ID from String to its proper format and retrieve account
        // information asynchronously
        return Mono.fromFuture(manager.getAccountWithId(ApolloHelpers.parseAccountId(accountId)))
                // If no account is found, return an HTTP 404 error
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                // Convert the retrieved account information into the GetAccount DTO format
                .map(GetAccount::new);
    }

    /*
     * Status Actions Endpoints
     * ======================================
     * - POST /api/statuses
     * - PUT /api/statuses/{id}
     * - DELETE /api/statuses/{id}
     * - GET /api/statuses/{id}/source
     * - GET /api/statuses/{id}/context
     * - GET /api/statuses/{id}/context/ancestors
     * - GET /api/statuses/{id}/context/descendents
     * ======================================
     */
    

    @PostMapping(value = "/api/statuses", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Mono<Object> postStatus(WebSession session, @RequestBody(required = true) PostStatus params) {
        long requestAccountId = getMandatoryAccountId(session);
        validateStatus(params.status, params.spoiler_text, params.language, params.poll);
        if (params.scheduled_at != null) {
            return Mono.fromFuture(manager.postScheduledStatus(requestAccountId, params, null))
                    .map(GetScheduledStatus::new);
        } else
            return Mono.fromFuture(manager.postStatus(requestAccountId, params)).map(GetStatus::new);
    }

    @PostMapping(value = "/api/statuses", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<Object> postStatus(
            WebSession session,
            @RequestPart(value = "status", required = false) String status,
            @RequestPart(value = "in_reply_to_id", required = false) String in_reply_to_id,
            @RequestPart(value = "sensitive", required = false) String sensitive,
            @RequestPart(value = "spoiler_text", required = false) String spoiler_text,
            @RequestPart(value = "visibility", required = false) String visibility,
            @RequestPart(value = "language", required = false) String language,
            @RequestPart(value = "scheduled_at", required = false) String scheduled_at,
            @RequestPart(value = "media_ids[]", required = false) List<Part> media_ids,
            @RequestPart(value = "poll[options][]", required = false) List<Part> poll_options,
            @RequestPart(value = "poll[expires_in]", required = false) String poll_expires_in,
            @RequestPart(value = "poll[multiple]", required = false) String poll_multiple) {
        List<Mono<DataBuffer>> mediaContent = media_ids.stream().map(part -> part.content().single())
                .collect(Collectors.toList());
        List<Mono<DataBuffer>> pollOptions = poll_options.stream().map(part -> part.content().single())
                .collect(Collectors.toList());
        return (mediaContent.size() > 0 ? Mono.zip(mediaContent,
                res -> Arrays.stream(res).map(b -> ((DataBuffer) b).toString(Charset.defaultCharset()))
                        .collect(Collectors.toList()))
                : Mono.just(new ArrayList<>()))
                .flatMap(mediaContentResults -> (pollOptions.size() > 0 ? Mono.zip(pollOptions,
                        res -> Arrays.stream(res).map(b -> ((DataBuffer) b).toString(Charset.defaultCharset()))
                                .collect(Collectors.toList()))
                        : Mono.just(new ArrayList<>()))
                        .flatMap(pollOptionsResults -> {
                            PostStatus postStatus = new PostStatus(status, in_reply_to_id, visibility, scheduled_at);
                            postStatus.spoiler_text = spoiler_text;
                            postStatus.language = language;
                            postStatus.sensitive = sensitive != null ? Boolean.parseBoolean(sensitive) : null;
                            postStatus.media_ids = (List<String>) mediaContentResults;
                            if (pollOptionsResults.size() > 0 && poll_expires_in != null) {
                                postStatus.poll = new PostStatus.Poll();
                                postStatus.poll.options = (List<String>) pollOptionsResults;
                                postStatus.poll.expires_in = Long.parseLong(poll_expires_in);
                                postStatus.poll.multiple = poll_multiple != null ? Boolean.parseBoolean(poll_multiple)
                                        : false;
                            }
                            return this.postStatus(session, postStatus);
                        }));
    }

    @PutMapping(value = "/api/statuses/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Mono<GetStatus> putStatus(WebSession session, @PathVariable("id") String id, @RequestBody(required = true) PutStatus params) {
        long requestAccountId = getMandatoryAccountId(session);
        validateStatus(params.status, params.spoiler_text, params.language, params.poll);
        StatusPointer statusPointer = ApolloHelpers.parseStatusPointer(id);
        if (statusPointer.authorId != requestAccountId) return Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND));
        return Mono.fromFuture(manager.putStatus(statusPointer, params))
                   .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                   .map(GetStatus::new);
    }

    @PutMapping(value = "/api/statuses/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<GetStatus> putStatus(
            WebSession session,
            @PathVariable("id") String id,
            @RequestPart(value = "status", required = false) String status,
            @RequestPart(value = "sensitive", required = false) String sensitive,
            @RequestPart(value = "spoiler_text", required = false) String spoiler_text,
            @RequestPart(value = "language", required = false) String language,
            @RequestPart(value = "media_ids[]", required = false) List<Part> media_ids
    ) {
        List<Mono<DataBuffer>> mediaContent = media_ids.stream().map(part -> part.content().single()).collect(Collectors.toList());
        return (mediaContent.size() > 0 ? Mono.zip(mediaContent, res -> Arrays.stream(res).map(b -> ((DataBuffer) b).toString(Charset.defaultCharset())).collect(Collectors.toList())) : Mono.just(new ArrayList<>()))
                .flatMap(mediaContentResults -> {
                    PutStatus putStatus = new PutStatus(status);
                    putStatus.spoiler_text = spoiler_text;
                    putStatus.language = language;
                    putStatus.sensitive = sensitive != null ? Boolean.parseBoolean(sensitive) : null;
                    putStatus.media_ids = (List<String>) mediaContentResults;
                    return this.putStatus(session, id, putStatus);
                });
    }

    @DeleteMapping("/api/statuses/{id}")
    public Mono<GetStatus> deleteStatus(WebSession session, @PathVariable("id") String id) {
        long requestAccountId = getMandatoryAccountId(session);
        StatusPointer statusPointer = ApolloHelpers.parseStatusPointer(id);
        if (statusPointer.authorId != requestAccountId) return Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND));
        return Mono.fromFuture(manager.getStatus(requestAccountId, statusPointer))
                   .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                   .flatMap(status -> Mono.zip(Mono.just(status), Mono.fromFuture(manager.deleteStatus(requestAccountId, statusPointer.statusId))))
                   .map(Tuple2::getT1)
                   .map(GetStatus::new);
    }

    @GetMapping("/api/statuses/{id}/source")
    public Mono<GetStatusSource> getStatusSource(WebSession session, @PathVariable("id") String id) {
        Long requestAccountId = (Long) session.getAttributes().get("accountId"); // allowed to be null
        StatusPointer statusPointer = ApolloHelpers.parseStatusPointer(id);
        return Mono.fromFuture(manager.getStatus(requestAccountId, statusPointer))
                   .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                   .map(GetStatusSource::new);
    }

    @GetMapping("/api/v1/statuses/{id}/context")
    public Mono<GetContext> getContext(WebSession session, @PathVariable("id") String id) {
        Long requestAccountId = (Long) session.getAttributes().get("accountId"); // allowed to be null
        StatusPointer statusPointer = ApolloHelpers.parseStatusPointer(id);
        return Mono.zip(Mono.fromFuture(manager.getAncestors(requestAccountId, statusPointer)),
                        Mono.fromFuture(manager.getDescendants(requestAccountId, statusPointer)))
                   .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                   .map(result -> new GetContext(
                           ApolloApiHelpers.createGetStatuses(result.getT1()),
                           ApolloApiHelpers.createGetStatuses(result.getT2())
                   ));
    }

    @GetMapping("/api/statuses/{id}/context/ancestors")
public Mono<List<GetStatus>> getAncestors(WebSession session, @PathVariable("id") String id) {
    Long requestAccountId = (Long) session.getAttributes().get("accountId"); // allowed to be null

    StatusPointer statusPointer = ApolloHelpers.parseStatusPointer(id);

    return Mono.fromFuture(manager.getAncestors(requestAccountId, statusPointer))
            .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
            .map(ApolloApiHelpers::createGetStatuses);
}

@GetMapping("/api/statuses/{id}/context/descendants")
public Mono<List<GetStatus>> getDescendants(WebSession session, @PathVariable("id") String id) {
    Long requestAccountId = (Long) session.getAttributes().get("accountId"); // allowed to be null

    StatusPointer statusPointer = ApolloHelpers.parseStatusPointer(id);

    return Mono.fromFuture(manager.getDescendants(requestAccountId, statusPointer))
            .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
            .map(ApolloApiHelpers::createGetStatuses);
}

    /*
     * Scheduled Status Actions Endpoints
     * ======================================
     * - GET /api/scheduled_statuses
     * - PUT /api/scheduled_statuses/{id}
     * - DELETE /api/scheduled_statuses/{id}
     * ======================================
     */

    @GetMapping("/api/scheduled_statuses")
    public Mono<List<GetScheduledStatus>> getScheduledStatuses(ServerWebExchange exchange, WebSession session,
            @RequestParam(required = false) String max_id,
            @RequestParam(required = false) Integer limit) {
        long requestAccountId = getMandatoryAccountId(session);
        StatusPointer statusPointer = ApolloHelpers.parseStatusPointer(max_id);
        return Mono.fromFuture(manager.getScheduledStatuses(requestAccountId, statusPointer, limit))
                .map(queryResults -> {
                    ApolloApiHelpers.setLinkHeader(exchange, queryResults);
                    return queryResults.results.stream()
                            .map(GetScheduledStatus::new)
                            .collect(Collectors.toList());
                });
    }

    @GetMapping("/api/scheduled_statuses/{id}")
    public Mono<GetScheduledStatus> getScheduledStatus(WebSession session, @PathVariable("id") String id) {
        long requestAccountId = getMandatoryAccountId(session);
        StatusPointer statusPointer = ApolloHelpers.parseStatusPointer(id);
        if (statusPointer.authorId != requestAccountId)
            return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        return Mono.fromFuture(manager.getScheduledStatus(statusPointer))
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                .map(GetScheduledStatus::new);
    }

    @PutMapping("/api/scheduled_statuses/{id}")
    public Mono<GetScheduledStatus> updateScheduledStatus(WebSession session,
            @PathVariable("id") String id,
            @RequestBody PutScheduledStatus putScheduledStatus) {
        long requestAccountId = getMandatoryAccountId(session);
        StatusPointer statusPointer = ApolloHelpers.parseStatusPointer(id);
        if (statusPointer.authorId != requestAccountId)
            return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        return Mono.fromFuture(manager.updateScheduledStatus(statusPointer, putScheduledStatus.scheduled_at))
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                .map(GetScheduledStatus::new);
    }

    @DeleteMapping("/api/scheduled_statuses/{id}")
    public Mono<Void> cancelScheduledStatus(WebSession session, @PathVariable("id") String id) {
        long requestAccountId = getMandatoryAccountId(session);
        StatusPointer statusPointer = ApolloHelpers.parseStatusPointer(id);
        if (statusPointer.authorId != requestAccountId)
            return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        return Mono.fromFuture(manager.cancelScheduledStatus(statusPointer));
    }

    /*
     * Follow Actions Endpoints
     * ======================================
     * - POST /api/accounts/{id}/follow
     * - POST /api/accounts/{id}/unfollow
     * - GET api/accounts/{id}/following
     * - GET api/accounts/{id}/followers
     * ======================================
     */

    @PostMapping("/api/accounts/{id}/follow")
    public Mono<GetRelationship> postFollowAccount(WebSession session, @PathVariable("id") String id,
            @RequestBody(required = false) PostFollow params) {
        long requestAccountId = getMandatoryAccountId(session);
        long followeeId = ApolloHelpers.parseAccountId(id);

        return Mono.fromFuture(manager.getAccountWithIdPair(requestAccountId, followeeId))
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                .flatMap(accountWithIdPair -> {
                    return Mono.fromFuture(manager.postFollowAccount(requestAccountId, followeeId, params));
                })
                .flatMap(result -> Mono.fromFuture(manager.getAccountRelationship(requestAccountId, followeeId)))
                .map(result -> new GetRelationship(id, result));
    }

    @PostMapping("/api/accounts/{id}/unfollow")
    public Mono<GetRelationship> postUnfollowAccount(WebSession session, @PathVariable("id") String id) {
        long requestAccountId = getMandatoryAccountId(session);
        long followeeId = ApolloHelpers.parseAccountId(id);

        return Mono.fromFuture(manager.getAccountWithIdPair(requestAccountId, followeeId))
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                .flatMap(accountWithIdPair -> {
                    return Mono.fromFuture(manager.postRemoveFollowAccount(requestAccountId, followeeId));
                })
                .flatMap(result -> Mono.fromFuture(manager.getAccountRelationship(requestAccountId, followeeId)))
                .map(result -> new GetRelationship(id, result));
    }

        @GetMapping("/api/accounts/{id}/following")
    public Mono<List<GetAccount>> getAccountFollowees(ServerWebExchange exchange, @PathVariable("id") String accountId, @RequestParam(required = false) Long max_id, @RequestParam(required = false) Integer limit) {
        return Mono.fromFuture(manager.getAccountFollowees(ApolloHelpers.parseAccountId(accountId), max_id, limit))
                   .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                   .map(queryResults -> {
                       ApolloApiHelpers.setLinkHeader(exchange, queryResults);
                       return ApolloApiHelpers.createGetAccounts(queryResults.results);
                   });
    }

    @GetMapping("/api/accounts/{id}/followers")
    public Mono<List<GetAccount>> getAccountFollowers(ServerWebExchange exchange, @PathVariable("id") String accountId, @RequestParam(required = false) Long max_id, @RequestParam(required = false) Integer limit) {
        return Mono.fromFuture(manager.getAccountFollowers(ApolloHelpers.parseAccountId(accountId), max_id, limit))
                   .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                   .map(queryResults -> {
                       ApolloApiHelpers.setLinkHeader(exchange, queryResults);
                       return ApolloApiHelpers.createGetAccounts(queryResults.results);
                   });
    }

    /*
     * Suppression Actions Endpoints
     * ======================================
     * - POST /api/accounts/{id}/mute
     * - POST /api/accounts/{id}/unmute
     * - GET /api/blocks
     * - POST /api/accounts/{id}/block
     * - POST /api/accounts/{id}/unblock
     * - POST /api/accounts/{id}/remove_from_followers
     * ======================================
     */

    @PostMapping("/api/accounts/{id}/mute")
    public Mono<GetRelationship> postMuteAccount(WebSession session, @PathVariable("id") String id,
            @RequestBody(required = true) PostMute params) {
        long requestAccountId = getMandatoryAccountId(session);
        long muteeId = ApolloHelpers.parseAccountId(id);
        return Mono.fromFuture(manager.postMuteAccount(requestAccountId, muteeId, params))
                .flatMap(result -> Mono.fromFuture(manager.getAccountRelationship(requestAccountId, muteeId)))
                .map(result -> new GetRelationship(id, result));
    }

    @PostMapping("/api/accounts/{id}/unmute")
    public Mono<GetRelationship> postUnmuteAccount(WebSession session, @PathVariable("id") String id) {
        long requestAccountId = getMandatoryAccountId(session);
        long muteeId = ApolloHelpers.parseAccountId(id);
        return Mono.fromFuture(manager.postRemoveMuteAccount(requestAccountId, muteeId))
                .flatMap(result -> Mono.fromFuture(manager.getAccountRelationship(requestAccountId, muteeId)))
                .map(result -> new GetRelationship(id, result));
    }

    @GetMapping("/api/blocks")
    public Mono<List<GetAccount>> getBlocks(ServerWebExchange exchange, WebSession session, @RequestParam(required = false) String max_id, @RequestParam(required = false) Integer limit) {
        long requestAccountId = getMandatoryAccountId(session);
        return Mono.fromFuture(manager.getBlocks(requestAccountId, ApolloHelpers.parseAccountId(max_id), limit))
                   .map(queryResults -> {
                       ApolloApiHelpers.setLinkHeader(exchange, queryResults);
                       return ApolloApiHelpers.createGetAccounts(queryResults.results);
                   });
    }

    @PostMapping("/api/accounts/{id}/block")
    public Mono<GetRelationship> postBlockAccount(WebSession session, @PathVariable("id") String id) {
        long requestAccountId = getMandatoryAccountId(session);
        long blockeeId = ApolloHelpers.parseAccountId(id);

        return Mono.fromFuture(manager.getAccountWithIdPair(requestAccountId, blockeeId))
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                .flatMap(accountWithIdPair -> {
                    return Mono.fromFuture(manager.postBlockAccount(requestAccountId, blockeeId));
                })
                .flatMap(result -> Mono.fromFuture(manager.getAccountRelationship(requestAccountId, blockeeId)))
                .map(result -> new GetRelationship(id, result));
    }

    @PostMapping("/api/accounts/{id}/unblock")
    public Mono<GetRelationship> postUnblockAccount(WebSession session, @PathVariable("id") String id) {
        long requestAccountId = getMandatoryAccountId(session);
        long blockeeId = ApolloHelpers.parseAccountId(id);

        return Mono.fromFuture(manager.getAccountWithIdPair(requestAccountId, blockeeId))
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                .flatMap(accountWithIdPair -> {
                    return Mono.fromFuture(manager.postRemoveBlockAccount(requestAccountId, blockeeId));
                })
                .flatMap(result -> Mono.fromFuture(manager.getAccountRelationship(requestAccountId, blockeeId)))
                .map(result -> new GetRelationship(id, result));
    }

    @PostMapping("/api/accounts/{id}/remove_from_followers")
    public Mono<GetRelationship> postRemoveFromFollowers(WebSession session, @PathVariable("id") String id) {
        long requestAccountId = getMandatoryAccountId(session);
        long followerId = ApolloHelpers.parseAccountId(id);
        return Mono.fromFuture(manager.postRemoveFollowAccount(followerId, requestAccountId))
                   .flatMap(result -> Mono.fromFuture(manager.getAccountRelationship(requestAccountId, followerId)))
                   .map(result -> new GetRelationship(id, result));
    }

    /*
     * Pin Actions Endpoints
     * ======================================
     * - POST /api/accounts/{id}/pin
     * - POST /api/accounts/{id}/unpin
     * ======================================
     */

    @PostMapping("/api/accounts/{id}/pin")
    public Mono<GetRelationship> postFeatureAccount(WebSession session, @PathVariable("id") String id) {
        long requestAccountId = getMandatoryAccountId(session);
        long featureeId = ApolloHelpers.parseAccountId(id);
        return Mono.fromFuture(manager.postFeatureAccount(requestAccountId, featureeId))
                   .flatMap(result -> Mono.fromFuture(manager.getAccountRelationship(requestAccountId, featureeId)))
                   .map(result -> new GetRelationship(id, result));
    }

    @PostMapping("/api/accounts/{id}/unpin")
    public Mono<GetRelationship> postRemoveFeatureAccount(WebSession session, @PathVariable("id") String id) {
        long requestAccountId = getMandatoryAccountId(session);
        long featureeId = ApolloHelpers.parseAccountId(id);
        return Mono.fromFuture(manager.postRemoveFeatureAccount(requestAccountId, featureeId))
                   .flatMap(result -> Mono.fromFuture(manager.getAccountRelationship(requestAccountId, featureeId)))
                   .map(result -> new GetRelationship(id, result));
    }

    /*
     * Conversation Actions Endpoints
     * ======================================
     * - POST /api/conversations/{id}/read
     * - GET /api/conversations
     * - DELETE /api/conversations/{id}
     * ======================================
     */

    @PostMapping("/api/conversations/{id}/read")
    public Mono<GetConversation> postConversation(WebSession session, @PathVariable("id") Long conversationId) {
        long requestAccountId = getMandatoryAccountId(session);
        return Mono.fromFuture(manager.postConversation(requestAccountId, conversationId, false))
                   .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                   .map(GetConversation::new);
    }

    @GetMapping("/api/conversations")
    public Mono<List<GetConversation>> getConversations(ServerWebExchange exchange, WebSession session, @RequestParam(required = false) Long max_id, @RequestParam(required = false) Integer limit) {
        long requestAccountId = getMandatoryAccountId(session);
        return Mono.fromFuture(manager.getConversationTimeline(requestAccountId, max_id, limit))
                   .map(queryResults -> {
                       ApolloApiHelpers.setLinkHeader(exchange, queryResults);
                       return ApolloApiHelpers.createGetConversations(queryResults.results);
                   });
    }

    @DeleteMapping("/api/conversations/{id}")
    public Mono deleteConversation(WebSession session, @PathVariable("id") Long conversationId) {
        long requestAccountId = getMandatoryAccountId(session);
        return Mono.fromFuture(manager.deleteConversation(requestAccountId, conversationId))
                   .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                   .map(result -> new HashMap());
    }

    /*
     * Trends Actions Endpoints
     * ======================================
     * - GET /api/trends
     * - GET /api/trends/statuses
     * ======================================
     */


    @GetMapping("/api/trends")
    public Mono<List<GetTag>> getTrendingTags(@RequestParam(required = false) Integer limit, @RequestParam(required = false) Integer offset) {
        return Mono.fromFuture(manager.getTrendingHashtags(limit, offset)).map(ApolloApiHelpers::createGetTags);
    }

    @GetMapping("/api/v1/trends/statuses")
    public Mono<List<GetStatus>> getTrendingStatuses(WebSession session, @RequestParam(required = false) Integer limit, @RequestParam(required = false) Integer offset) {
        Long requestAccountId = (Long) session.getAttributes().get("accountId"); // allowed to be null
        return Mono.fromFuture(manager.getTrendingStatuses(requestAccountId, limit, offset)).map(ApolloApiHelpers::createGetStatuses);
    }

    /*
     * Follow Requests Actions Endpoints
     * ======================================
     * - GET /api/follow_requests
     * - POST /api/follow_requests/{id}/authorize
     * - POST /api/follow_requests/{id}/reject
     * ======================================
     */

    @GetMapping("/api/follow_requests")
    public Mono<List<GetAccount>> getFollowRequests(ServerWebExchange exchange, WebSession session, @RequestParam(required = false) Long max_id, @RequestParam(required = false) Integer limit) {
        long requestAccountId = getMandatoryAccountId(session);
        return Mono.fromFuture(manager.getFollowRequests(requestAccountId, max_id, limit))
                   .map(queryResults -> {
                    ApolloApiHelpers.setLinkHeader(exchange, queryResults);
                       return ApolloApiHelpers.createGetAccounts(queryResults.results);
                   });
    }

    @PostMapping("/api/follow_requests/{id}/authorize")
    public Mono<GetRelationship> acceptFollowRequest(WebSession session, @PathVariable("id") String id) {
        long requestAccountId = getMandatoryAccountId(session);
        long requesterId = ApolloHelpers.parseAccountId(id);
        return Mono.fromFuture(manager.acceptFollowRequest(requestAccountId, requesterId))
                   .filter(isRequestExists -> isRequestExists)
                   .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                   .flatMap(result -> Mono.fromFuture(manager.getAccountRelationship(requestAccountId, requesterId)))
                   .map(result -> new GetRelationship(id, result));
    }

    @PostMapping("/api/follow_requests/{id}/reject")
    public Mono<GetRelationship> rejectFollowRequest(WebSession session, @PathVariable("id") String id) {
        long requestAccountId = getMandatoryAccountId(session);
        long requesterId = ApolloHelpers.parseAccountId(id);
        return Mono.fromFuture(manager.rejectFollowRequest(requestAccountId, requesterId))
                   .filter(isRequestExists -> isRequestExists)
                   .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                   .flatMap(result -> Mono.fromFuture(manager.getAccountRelationship(requestAccountId, requesterId)))
                   .map(result -> new GetRelationship(id, result));
    }

    /*
     * Status Interactions Endpoints
     * ======================================
     * - POST /api/statuses/{id}/like
     * - POST /api/statuses/{id}/unlike
     * - POST /api/statuses/{id}/repost
     * - POST /api/statuses/{id}/unrepost
     * - POST /api/statuses/{id}/bookmark
     * - POST /api/statuses/{id}/unbookmark
     * - POST /api/statuses/{id}/mute
     * - POST /api/statuses/{id}/unmute
     * - POST /api/statuses/{id}/pin
     * - POST /api/statuses/{id}/unpin
     * ======================================
     */



     @PostMapping("/api/statuses/{id}/like")
     public Mono<GetStatus> postFavoriteStatus(WebSession session, @PathVariable("id") String id) {
         long requestAccountId = getMandatoryAccountId(session);
     
         StatusPointer statusPointer = ApolloHelpers.parseStatusPointer(id);
     
         return Mono.fromFuture(manager.getStatus(requestAccountId, statusPointer))
                 .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                 .flatMap(statusQueryResult -> Mono.fromFuture(manager.postLikeStatus(requestAccountId, statusPointer)))
                 .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                 .map(GetStatus::new);
     }

     @PostMapping("/api/statuses/{id}/unlike")
     public Mono<GetStatus> postRemoveFavoriteStatus(WebSession session, @PathVariable("id") String id) {
         long requestAccountId = getMandatoryAccountId(session);
     
         StatusPointer statusPointer = ApolloHelpers.parseStatusPointer(id);
     
         return Mono.fromFuture(manager.getStatus(requestAccountId, statusPointer))
                 .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                 .flatMap(statusQueryResult -> Mono.fromFuture(manager.postRemoveLikeStatus(requestAccountId, statusPointer)))
                 .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                 .map(GetStatus::new);
     }

    @PostMapping("/api/statuses/{id}/repost")
    public Mono<GetStatus> postBoostStatus(WebSession session, @PathVariable("id") String id) {
        long requestAccountId = getMandatoryAccountId(session);
        StatusPointer statusPointer = ApolloHelpers.parseStatusPointer(id);
        return Mono.fromFuture(manager.postBoostStatus(requestAccountId, statusPointer))
                   .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                   .map(GetStatus::new);
    }

    @PostMapping("/api/statuses/{id}/unrepost")
    public Mono<GetStatus> postRemoveBoostStatus(WebSession session, @PathVariable("id") String id) {
        long requestAccountId = getMandatoryAccountId(session);
        StatusPointer statusPointer = ApolloHelpers.parseStatusPointer(id);
        return Mono.fromFuture(manager.postRemoveBoostStatus(requestAccountId, statusPointer))
                   .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                   .map(GetStatus::new);
    }

    @PostMapping("/api/statuses/{id}/bookmark")
    public Mono<GetStatus> postBookmarkStatus(WebSession session, @PathVariable("id") String id) {
        long requestAccountId = getMandatoryAccountId(session);
        StatusPointer statusPointer = ApolloHelpers.parseStatusPointer(id);
        return Mono.fromFuture(manager.postBookmarkStatus(requestAccountId, statusPointer))
                   .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                   .map(GetStatus::new);
    }

    @PostMapping("/api/statuses/{id}/unbookmark")
    public Mono<GetStatus> postRemoveBookmarkStatus(WebSession session, @PathVariable("id") String id) {
        long requestAccountId = getMandatoryAccountId(session);
        StatusPointer statusPointer = ApolloHelpers.parseStatusPointer(id);
        return Mono.fromFuture(manager.postRemoveBookmarkStatus(requestAccountId, statusPointer))
                   .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                   .map(GetStatus::new);
    }

    @PostMapping("/api/statuses/{id}/mute")
    public Mono<GetStatus> postMuteStatus(WebSession session, @PathVariable("id") String id) {
        long requestAccountId = getMandatoryAccountId(session);
        StatusPointer statusPointer = ApolloHelpers.parseStatusPointer(id);
        return Mono.fromFuture(manager.postMuteStatus(requestAccountId, statusPointer))
                   .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                   .map(GetStatus::new);
    }

    @PostMapping("/api/statuses/{id}/unmute")
    public Mono<GetStatus> postRemoveMuteStatus(WebSession session, @PathVariable("id") String id) {
        long requestAccountId = getMandatoryAccountId(session);
        StatusPointer statusPointer = ApolloHelpers.parseStatusPointer(id);
        return Mono.fromFuture(manager.postRemoveMuteStatus(requestAccountId, statusPointer))
                   .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                   .map(GetStatus::new);
    }

    @PostMapping("/api/statuses/{id}/pin")
    public Mono<GetStatus> postPinStatus(WebSession session, @PathVariable("id") String id) {
        long requestAccountId = getMandatoryAccountId(session);
        StatusPointer statusPointer = ApolloHelpers.parseStatusPointer(id);
        if (statusPointer.authorId != requestAccountId) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY);
        return Mono.fromFuture(manager.postPinStatus(requestAccountId, statusPointer))
                   .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY)))
                   .map(GetStatus::new);
    }

    @PostMapping("/api/statuses/{id}/unpin")
    public Mono<GetStatus> postRemovePinStatus(WebSession session, @PathVariable("id") String id) {
        long requestAccountId = getMandatoryAccountId(session);
        StatusPointer statusPointer = ApolloHelpers.parseStatusPointer(id);
        if (statusPointer.authorId != requestAccountId) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY);
        return Mono.fromFuture(manager.postRemovePinStatus(requestAccountId, statusPointer))
                   .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY)))
                   .map(GetStatus::new);
    }

         /*
     * User Metrics Endpoints
     * ======================================
     * - GET /api/bookmarks
     * ======================================
     */


    @GetMapping("/api/bookmarks")
    public Mono<List<GetStatus>> getBookmarks(ServerWebExchange exchange, WebSession session, @RequestParam(required = false) String max_id, @RequestParam(required = false) Integer limit) {
        long requestAccountId = getMandatoryAccountId(session);
        StatusPointer statusPointer = ApolloHelpers.parseStatusPointer(max_id);
        return Mono.fromFuture(manager.getBookmarks(requestAccountId, statusPointer, limit))
                   .map(statusQueryResults -> {
                       ApolloApiHelpers.setStatusLinkHeader(exchange, statusQueryResults);
                       return ApolloApiHelpers.createGetStatuses(statusQueryResults);
                   });
    }



}
