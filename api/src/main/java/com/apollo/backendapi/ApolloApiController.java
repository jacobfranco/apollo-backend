package com.apollo.backendapi;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.charset.Charset;
import java.util.AbstractMap.SimpleEntry;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.apache.commons.io.FilenameUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.*;
import org.springframework.http.codec.multipart.*;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.*;

import com.apollo.backend.ApolloHelpers;
import com.apollo.backend.data.*;
import com.apollo.backendapi.pojos.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import reactor.core.publisher.Mono;

@RestController
@CrossOrigin(exposedHeaders = { "Link" })
public class ApolloApiController {

    private static final Logger logger = LogManager.getLogger(ApolloApiController.class);
    private static final int QUERY_PARAM_ARRAY_SIZE_LIMIT = 200;

    public static ApolloApiManager manager;

    /*
     * Helper Functions and Classes
     * ======================================
     * - loginWithAccount
     * - getMandatoryAccountId
     * - validateStatus
     * - accountAttachment
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

    private static class AccountAttachment {
        public AttachmentWithId attachmentWithId;
        public File file;
        public Part part;
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
    public Mono<GetApplication> postApplication(@RequestBody(required = true) PostApplication params) {
        return Mono.fromFuture(manager.postApplication(params))
                .map(GetApplication::new);
    }

    @GetMapping("/api/apps/{clientId}")
    public Mono<GetApplication> getApplication(@PathVariable String clientId) {
        return Mono.fromFuture(manager.getApplication(clientId))
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Application not found")))
                .map(GetApplication::new);
    }

    // Define a controller method to handle POST requests for application
    // registration with form URL encoded payload
    @PostMapping(value = "/api/apps", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public Mono<GetApplication> postApplication(ServerWebExchange exchange) {
        return exchange.getFormData()
                .flatMap(formParams -> {
                    PostApplication params = ApolloApiFormParser.parseParams(formParams, new PostApplication());
                    return this.postApplication(params);
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
        return exchange.getFormData()
                .flatMap(formParams -> {
                    PostToken params = ApolloApiFormParser.parseParams(formParams, new PostToken());
                    return this.postOauthToken(session, params);
                });
    }

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
     * Account Actions Endpoints
     * ======================================
     * - POST /api/accounts
     * - GET /api/accounts/verify_credentials
     * - GET /api/accounts/{id}
     * - GET /api/accounts/search
     * - GET /api/accounts/lookup
     * - PATCH /api/accounts/update_credentials
     * - GET /api/accounts/{id}/statuses
     * ======================================
     */

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

    // TODO: Removed resolveURL stuff - make sure that didn't break anything
    @GetMapping("/api/accounts/search")
    public Mono<List<GetAccount>> getAccountSearch(
            ServerWebExchange exchange,
            WebSession session,
            @RequestParam(required = true) String q,
            @RequestParam(required = false) Boolean following,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Long offset,
            @RequestParam(required = false) Long start_next_id,
            @RequestParam(required = false) String start_term) throws MalformedURLException {
        long requestAccountId = getMandatoryAccountId(session);
        List<String> terms = Arrays.asList(q.toLowerCase().trim().split("\\s+"));

        Map startParams = ApolloApiHelpers.createSearchParams(start_next_id, start_term);
        return Mono.fromFuture((offset == null || offset == 0L)
                ? manager.getProfileSearch(requestAccountId, terms, startParams, limit, following != null && following)
                : CompletableFuture.completedFuture(
                        new ApolloApiManager.QueryResults<AccountWithId, Map>(new ArrayList<>(), true, null, null)))
                .map(queryResults -> {
                    ApolloApiHelpers.setLinkHeader(exchange, queryResults);
                    return ApolloApiHelpers.createGetAccounts(queryResults.results);
                });
    }

    @GetMapping("/api/accounts/lookup")
    public Mono<GetAccount> getAccountLookup(@RequestParam(required = false) String username) {
        return Mono.fromFuture(manager.getAccountId(username))
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                .flatMap(accountId -> Mono.fromFuture(manager.getAccountWithId(accountId)))
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                .map(GetAccount::new);
    }

    @GetMapping("/api/accounts/checkEmail")
    public Mono<Map<String, Boolean>> checkEmail(@RequestParam String email) {
        return Mono.fromFuture(manager.emailExists(email))
                .map(exists -> Map.of("exists", exists));
    }

    @PatchMapping(value = "/api/accounts/update_credentials", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<GetAccount> patchAccountUpdateCredentials(
            WebSession session,
            @RequestPart(value = "display_name", required = false) String display_name,
            @RequestPart(value = "note", required = false) String note,
            @RequestPart(value = "avatar", required = false) Part avatar,
            @RequestPart(value = "header", required = false) Part header,
            @RequestPart(value = "locked", required = false) String locked,
            @RequestPart(value = "bot", required = false) String bot,
            @RequestPart(value = "discoverable", required = false) String discoverable,
            @RequestPart(value = "fields_attributes[0][name]", required = false) String field0_name,
            @RequestPart(value = "fields_attributes[0][value]", required = false) String field0_value,
            @RequestPart(value = "fields_attributes[1][name]", required = false) String field1_name,
            @RequestPart(value = "fields_attributes[1][value]", required = false) String field1_value,
            @RequestPart(value = "fields_attributes[2][name]", required = false) String field2_name,
            @RequestPart(value = "fields_attributes[2][value]", required = false) String field2_value,
            @RequestPart(value = "fields_attributes[3][name]", required = false) String field3_name,
            @RequestPart(value = "fields_attributes[3][value]", required = false) String field3_value,
            @RequestPart(value = "source[privacy]", required = false) String privacy,
            @RequestPart(value = "source[sensitive]", required = false) String sensitive,
            @RequestPart(value = "source[language]", required = false) String language) throws JsonProcessingException {
        long requestAccountId = getMandatoryAccountId(session);
        // parse params
        Boolean isLocked = locked != null ? Boolean.parseBoolean(locked) : null;
        Boolean isBot = bot != null ? Boolean.parseBoolean(bot) : null;
        Boolean isDiscoverable = discoverable != null ? Boolean.parseBoolean(discoverable) : null;
        List<KeyValuePair> fields = new ArrayList<>();
        {
            if (field0_name != null && field0_value != null)
                fields.add(new KeyValuePair(ApolloApiHelpers.sanitizeField(field0_name),
                        ApolloApiHelpers.sanitizeField(field0_value)));
            if (field1_name != null && field1_value != null)
                fields.add(new KeyValuePair(ApolloApiHelpers.sanitizeField(field1_name),
                        ApolloApiHelpers.sanitizeField(field1_value)));
            if (field2_name != null && field2_value != null)
                fields.add(new KeyValuePair(ApolloApiHelpers.sanitizeField(field2_name),
                        ApolloApiHelpers.sanitizeField(field2_value)));
            if (field3_name != null && field3_value != null)
                fields.add(new KeyValuePair(ApolloApiHelpers.sanitizeField(field3_name),
                        ApolloApiHelpers.sanitizeField(field3_value)));
        }
        Map<String, String> newPrefs = new HashMap<>();
        {
            ObjectMapper objectMapper = new ObjectMapper();
            if (privacy != null) {
                Set<String> options = new HashSet<>(Arrays.asList("public", "unlisted", "private"));
                if (!options.contains(privacy))
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
                newPrefs.put("posting:default:visibility", objectMapper.writeValueAsString(privacy));
            }
            if (sensitive != null)
                newPrefs.put("posting:default:sensitive",
                        objectMapper.writeValueAsString(Boolean.parseBoolean(sensitive)));
            if (language != null) {
                if (language.length() > 3)
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST); // ISO 6391
                newPrefs.put("posting:default:language", objectMapper.writeValueAsString(language));
            }
        }
        // start the uploads
        List<Mono<Boolean>> uploads = new ArrayList<>();
        List<AccountAttachment> accountAttachments = new ArrayList<>();
        for (Part part : Arrays.asList(avatar, header)) {
            if (part == null)
                continue;
            // an empty upload is interpreted by Spring as a FormFieldPart
            // instead of a FilePart. in that case, we just make it blank.
            else if (part instanceof FormFieldPart) {
                AccountAttachment attachment = new AccountAttachment();
                attachment.attachmentWithId = new AttachmentWithId("", new Attachment(AttachmentKind.Image, "", ""));
                attachment.part = part;
                accountAttachments.add(attachment);
            } else {
                FilePart filePart = (FilePart) part;
                // determine the file type
                String ext = FilenameUtils.getExtension(filePart.filename()).toLowerCase();
                if (!ApolloApiConfig.IMAGE_EXTS.contains(ext))
                    throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Unrecognized file type");
                // transfer to static file dir
                File destDir = new File(ApolloApiConfig.STATIC_FILE_DIR, requestAccountId + "");
                destDir.mkdirs();
                String uuid = UUID.randomUUID().toString();
                File destFile = new File(destDir, String.format("%s.%s", uuid, ext));
                uploads.add(filePart.transferTo(destFile).then(Mono.just(true)));
                AccountAttachment attachment = new AccountAttachment();
                String path = String.format("%s/%s.%s", requestAccountId, uuid, ext);
                attachment.attachmentWithId = new AttachmentWithId(uuid,
                        new Attachment(AttachmentKind.Image, path, ""));
                attachment.file = destFile;
                attachment.part = filePart;
                accountAttachments.add(attachment);
            }
        }
        // ensure there's at least one mono so zip works correctly
        if (uploads.size() == 0)
            uploads.add(Mono.just(true));
        return Mono.zip(uploads, results -> true)
                // upload to s3 if enabled
                .flatMap(result -> {
                    if (ApolloApiConfig.S3_OPTIONS != null) {
                        List<Mono<Boolean>> s3Uploads = new ArrayList<>();
                        for (AccountAttachment accountAttachment : accountAttachments) {
                            if (accountAttachment.file == null)
                                continue;
                            String path = accountAttachment.attachmentWithId.attachment.path;
                            s3Uploads.add(
                                    Mono.fromFuture(ApolloApiHelpers.uploadToS3(ApolloApiConfig.S3_OPTIONS.bucketName,
                                            path, accountAttachment.file))
                                            .map(resp -> {
                                                accountAttachment.file.delete();
                                                return resp.sdkHttpResponse().isSuccessful();
                                            }));
                        }
                        // ensure there's at least one mono so zip works correctly
                        if (s3Uploads.size() > 0)
                            return Mono.zip(s3Uploads,
                                    results -> Arrays.stream(results).allMatch(success -> (boolean) success));
                    }
                    return Mono.just(true);
                })
                // check upload success and get account
                .flatMap(success -> {
                    if (!success)
                        throw new RuntimeException("Failed to connect to S3");
                    return Mono.fromFuture(manager.getAccountWithId(requestAccountId));
                })
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                // update the account
                .flatMap(accountWithId -> {
                    List<EditAccountField> edits = new ArrayList<>();
                    if (display_name != null)
                        edits.add(EditAccountField.displayName(
                                ApolloApiHelpers.sanitize(display_name, ApolloApiConfig.MAX_DISPLAY_NAME_LENGTH)));
                    if (note != null)
                        edits.add(
                                EditAccountField.bio(ApolloApiHelpers.sanitize(note, ApolloApiConfig.MAX_BIO_LENGTH)));
                    if (isLocked != null)
                        edits.add(EditAccountField.locked(isLocked));
                    if (isBot != null)
                        edits.add(EditAccountField.bot(isBot));
                    if (isDiscoverable != null)
                        edits.add(EditAccountField.discoverable(isDiscoverable));
                    if (fields.size() > 0)
                        edits.add(EditAccountField.fields(fields));
                    if (newPrefs.size() > 0) {
                        Map<String, String> prefs = new HashMap<>();
                        if (accountWithId.account.preferences != null)
                            prefs.putAll(accountWithId.account.preferences);
                        prefs.putAll(newPrefs);
                        edits.add(EditAccountField.preferences(prefs));
                    }
                    for (AccountAttachment accountAttachment : accountAttachments) {
                        if ("header".equals(accountAttachment.part.name()))
                            edits.add(EditAccountField.header(accountAttachment.attachmentWithId));
                        else if ("avatar".equals(accountAttachment.part.name()))
                            edits.add(EditAccountField.avatar(accountAttachment.attachmentWithId));
                    }
                    return Mono.fromFuture(manager.postEditAccount(requestAccountId, edits));
                })
                // query and return the updated account
                .flatMap(result -> Mono.fromFuture(manager.getAccountWithId(requestAccountId)))
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                // return account
                .map(GetAccount::new);
    }

    @PatchMapping(value = "/api/accounts/update_credentials", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Mono<GetAccount> patchAccountUpdateCredentials(WebSession session,
            @RequestBody(required = false) PatchUpdateCredentials params) throws JsonProcessingException {
        return patchAccountUpdateCredentials(session, params.display_name, params.note, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null);
    }

    @GetMapping("/api/accounts/{id}/statuses")
    public Mono<List<GetStatus>> getAccountStatuses(WebSession session, ServerWebExchange exchange,
            @PathVariable("id") String id,
            @RequestParam(required = false) String max_id,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Boolean only_media,
            @RequestParam(required = false) Boolean exclude_replies,
            @RequestParam(required = false) Boolean exclude_reposts,
            @RequestParam(required = false) Boolean pinned,
            @RequestParam(required = false) String tagged,
            @RequestParam(required = false) String space) {
        Long requestAccountId = (Long) session.getAttributes().get("accountId"); // allowed to be null
        StatusPointer statusPointer = ApolloHelpers.parseStatusPointer(max_id);
        long timelineAccountId = ApolloHelpers.parseAccountId(id);
        final CompletableFuture<StatusQueryResults> future;
        if (pinned != null && pinned)
            future = manager.getPinnedStatuses(requestAccountId, timelineAccountId);
        else if (only_media != null && only_media)
            future = manager.getAttachmentStatuses(requestAccountId, timelineAccountId, statusPointer, limit);
        else if (tagged != null)
            future = manager.getTaggedStatuses(requestAccountId, timelineAccountId, tagged, statusPointer, limit);
        else if (space != null)
            future = manager.getSpaceStatuses(requestAccountId, timelineAccountId, space, statusPointer, limit);
        else
            future = manager.getAccountTimeline(requestAccountId, timelineAccountId, statusPointer, limit,
                    exclude_replies == null || !exclude_replies, exclude_reposts == null || !exclude_reposts);
        return Mono.fromFuture(future)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                .map(statusQueryResults -> {
                    ApolloApiHelpers.setStatusLinkHeader(exchange, statusQueryResults);
                    return ApolloApiHelpers.createGetStatuses(statusQueryResults);
                });
    }

    /*
     * Account Relationship Action Endpoints
     * ======================================
     * - GET /api/accounts/relationships
     * - GET /api/accounts/familiar_followers
     * ======================================
     */

    @GetMapping("/api/accounts/relationships")
    public Mono<List<GetRelationship>> getAccountRelationships(WebSession session,
            @RequestParam(value = "id[]", required = false) List<String> idList,
            @RequestParam(value = "id", required = false) String id) {
        List<String> ids = new ArrayList<>();
        if (idList != null)
            ids.addAll(idList);
        if (id != null)
            ids.add(id);
        long requestAccountId = getMandatoryAccountId(session);
        if (ids.size() > QUERY_PARAM_ARRAY_SIZE_LIMIT)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        List<Mono<GetRelationship>> relationshipResults = new ArrayList<>();
        for (String targetIdStr : ids) {
            final long targetId;
            try {
                targetId = ApolloHelpers.parseAccountId(targetIdStr);
            } catch (RuntimeException e) {
                continue;
            }
            relationshipResults.add(
                    Mono.fromFuture(manager.getAccountRelationship(requestAccountId, targetId))
                            .map(result -> new GetRelationship(targetIdStr, result)));
        }
        return relationshipResults.size() > 0 ? Mono.zip(relationshipResults,
                results -> Arrays.stream(results).map(result -> (GetRelationship) result).collect(Collectors.toList()))
                : Mono.just(new ArrayList<>());
    }

    @GetMapping("/api/accounts/familiar_followers")
    public Mono<List<GetFamiliarFollowers>> getFamiliarFollowers(WebSession session,
            @RequestParam(value = "id[]", required = false) List<String> idList,
            @RequestParam(value = "id", required = false) String id) {
        List<String> ids = new ArrayList<>();
        if (idList != null)
            ids.addAll(idList);
        if (id != null)
            ids.add(id);
        long requestAccountId = getMandatoryAccountId(session);
        if (ids.size() > QUERY_PARAM_ARRAY_SIZE_LIMIT)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        List<Mono<List>> familiarFollowerIds = new ArrayList<>();
        for (String targetId : ids) {
            long parsed = ApolloHelpers.parseAccountId(targetId);
            if (parsed != requestAccountId)
                familiarFollowerIds.add(Mono.fromFuture(manager.getFamiliarFollowers(requestAccountId, parsed)));
        }
        return Mono.zip(familiarFollowerIds, results -> {
            List<GetFamiliarFollowers> familiarFollowers = new ArrayList<>();
            for (int i = 0; i < results.length; i++) {
                familiarFollowers.add(new GetFamiliarFollowers(ids.get(i),
                        ApolloApiHelpers.createGetAccounts((List<AccountWithId>) results[i])));
            }
            return familiarFollowers;
        });
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

    @GetMapping("/api/statuses/{id}")
    public Mono<GetStatus> getStatus(WebSession session, @PathVariable("id") String id) {
        Long requestAccountId = (Long) session.getAttributes().get("accountId"); // allowed to be null
        StatusPointer statusPointer = ApolloHelpers.parseStatusPointer(id);
        return Mono.fromFuture(manager.getStatus(requestAccountId, statusPointer))
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                .map(GetStatus::new);
    }

    @PutMapping(value = "/api/statuses/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Mono<GetStatus> putStatus(WebSession session, @PathVariable("id") String id,
            @RequestBody(required = true) PutStatus params) {
        long requestAccountId = getMandatoryAccountId(session);
        validateStatus(params.status, params.spoiler_text, params.language, params.poll);
        StatusPointer statusPointer = ApolloHelpers.parseStatusPointer(id);
        if (statusPointer.authorId != requestAccountId)
            return Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND));
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
            @RequestPart(value = "media_ids[]", required = false) List<Part> media_ids) {
        List<Mono<DataBuffer>> mediaContent = media_ids.stream().map(part -> part.content().single())
                .collect(Collectors.toList());
        return (mediaContent.size() > 0 ? Mono.zip(mediaContent,
                res -> Arrays.stream(res).map(b -> ((DataBuffer) b).toString(Charset.defaultCharset()))
                        .collect(Collectors.toList()))
                : Mono.just(new ArrayList<>()))
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
        StatusPointer pointer = ApolloHelpers.parseStatusPointer(id);
        if (pointer.authorId != requestAccountId) {
            return Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND));
        }
        return Mono.fromFuture(manager.deleteStatusInternal(pointer));
    }

    @GetMapping("/api/statuses/{id}/source")
    public Mono<GetStatusSource> getStatusSource(WebSession session, @PathVariable("id") String id) {
        Long requestAccountId = (Long) session.getAttributes().get("accountId"); // allowed to be null
        StatusPointer statusPointer = ApolloHelpers.parseStatusPointer(id);
        return Mono.fromFuture(manager.getStatus(requestAccountId, statusPointer))
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                .map(GetStatusSource::new);
    }

    @GetMapping("/api/statuses/{id}/context")
    public Mono<GetContext> getContext(WebSession session, @PathVariable("id") String id) {
        Long requestAccountId = (Long) session.getAttributes().get("accountId"); // allowed to be null
        StatusPointer statusPointer = ApolloHelpers.parseStatusPointer(id);
        return Mono.zip(Mono.fromFuture(manager.getAncestors(requestAccountId, statusPointer)),
                Mono.fromFuture(manager.getDescendants(requestAccountId, statusPointer)))
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                .map(result -> new GetContext(
                        ApolloApiHelpers.createGetStatuses(result.getT1()),
                        ApolloApiHelpers.createGetStatuses(result.getT2())));
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
    public Mono<List<GetAccount>> getAccountFollowees(ServerWebExchange exchange, @PathVariable("id") String accountId,
            @RequestParam(required = false) Long max_id, @RequestParam(required = false) Integer limit) {
        return Mono.fromFuture(manager.getAccountFollowees(ApolloHelpers.parseAccountId(accountId), max_id, limit))
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                .map(queryResults -> {
                    ApolloApiHelpers.setLinkHeader(exchange, queryResults);
                    return ApolloApiHelpers.createGetAccounts(queryResults.results);
                });
    }

    @GetMapping("/api/accounts/{id}/followers")
    public Mono<List<GetAccount>> getAccountFollowers(ServerWebExchange exchange, @PathVariable("id") String accountId,
            @RequestParam(required = false) Long max_id, @RequestParam(required = false) Integer limit) {
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
    public Mono<List<GetAccount>> getBlocks(ServerWebExchange exchange, WebSession session,
            @RequestParam(required = false) String max_id, @RequestParam(required = false) Integer limit) {
        long requestAccountId = getMandatoryAccountId(session);
        return Mono.fromFuture(manager.getBlocks(requestAccountId, ApolloHelpers.parseAccountId(max_id), limit))
                .map(queryResults -> {
                    ApolloApiHelpers.setLinkHeader(exchange, queryResults);
                    return ApolloApiHelpers.createGetAccounts(queryResults.results);
                });
    }

    @GetMapping("/api/mutes")
    public Mono<List<GetAccount>> getMutes(ServerWebExchange exchange, WebSession session,
            @RequestParam(required = false) String max_id, @RequestParam(required = false) Integer limit) {
        long requestAccountId = getMandatoryAccountId(session);
        return Mono.fromFuture(manager.getMutes(requestAccountId, ApolloHelpers.parseAccountId(max_id), limit))
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
    public Mono<List<GetConversation>> getConversations(ServerWebExchange exchange, WebSession session,
            @RequestParam(required = false) Long max_id, @RequestParam(required = false) Integer limit) {
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
     * - GET /api/trends/spaces
     * - GET /api/trends/statuses
     * ======================================
     */

    @GetMapping("/api/trends")
    public Mono<List<GetTag>> getTrendingTags(@RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Integer offset) {
        return Mono.fromFuture(manager.getTrendingHashtags(limit, offset)).map(ApolloApiHelpers::createGetTags);
    }

    @GetMapping("/api/trends/spaces")
    public Mono<List<GetSpace>> getTrendingSpaces(@RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Integer offset) {
        return Mono.fromFuture(manager.getTrendingSpaces(limit, offset))
                .map(stats -> stats.entrySet().stream()
                        .map(entry -> ApolloApiHelpers.createGetSpace(entry.getKey(),
                                ApolloApiHelpers.getSpaceNameFromId(entry.getKey()), entry.getValue(), false))
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList()));
    }

    @GetMapping("/api/trends/statuses")
    public Mono<List<GetStatus>> getTrendingStatuses(WebSession session, @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Integer offset) {
        Long requestAccountId = (Long) session.getAttributes().get("accountId"); // allowed to be null
        return Mono.fromFuture(manager.getTrendingStatuses(requestAccountId, limit, offset))
                .map(ApolloApiHelpers::createGetStatuses);
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
    public Mono<List<GetAccount>> getFollowRequests(ServerWebExchange exchange, WebSession session,
            @RequestParam(required = false) Long max_id, @RequestParam(required = false) Integer limit) {
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
     * - GET /api/statuses/{id}/reposted_by
     * - GET /api/statuses/{id}/liked_by
     * ======================================
     */

    @PostMapping("/api/statuses/{id}/like")
    public Mono<GetStatus> postLikeStatus(WebSession session, @PathVariable("id") String id) {
        long requestAccountId = getMandatoryAccountId(session);

        StatusPointer statusPointer = ApolloHelpers.parseStatusPointer(id);

        return Mono.fromFuture(manager.getStatus(requestAccountId, statusPointer))
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                .flatMap(statusQueryResult -> Mono.fromFuture(manager.postLikeStatus(requestAccountId, statusPointer)))
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                .map(GetStatus::new);
    }

    @PostMapping("/api/statuses/{id}/unlike")
    public Mono<GetStatus> postRemoveLikeStatus(WebSession session, @PathVariable("id") String id) {
        long requestAccountId = getMandatoryAccountId(session);

        StatusPointer statusPointer = ApolloHelpers.parseStatusPointer(id);

        return Mono.fromFuture(manager.getStatus(requestAccountId, statusPointer))
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                .flatMap(statusQueryResult -> Mono
                        .fromFuture(manager.postRemoveLikeStatus(requestAccountId, statusPointer)))
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
        if (statusPointer.authorId != requestAccountId)
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY);
        return Mono.fromFuture(manager.postPinStatus(requestAccountId, statusPointer))
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY)))
                .map(GetStatus::new);
    }

    @PostMapping("/api/statuses/{id}/unpin")
    public Mono<GetStatus> postRemovePinStatus(WebSession session, @PathVariable("id") String id) {
        long requestAccountId = getMandatoryAccountId(session);
        StatusPointer statusPointer = ApolloHelpers.parseStatusPointer(id);
        if (statusPointer.authorId != requestAccountId)
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY);
        return Mono.fromFuture(manager.postRemovePinStatus(requestAccountId, statusPointer))
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY)))
                .map(GetStatus::new);
    }

    @GetMapping("/api/statuses/{id}/reposted_by")
    public Mono<List<GetAccount>> getStatusBoosters(ServerWebExchange exchange, WebSession session,
            @PathVariable("id") String id, @RequestParam(required = false) String max_id,
            @RequestParam(required = false) Integer limit) {
        long requestAccountId = getMandatoryAccountId(session);
        StatusPointer statusPointer = ApolloHelpers.parseStatusPointer(id);
        return Mono
                .fromFuture(manager.getStatusBoosters(requestAccountId, statusPointer.authorId, statusPointer.statusId,
                        ApolloHelpers.parseAccountId(max_id), limit))
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                .map(queryResults -> {
                    ApolloApiHelpers.setLinkHeader(exchange, queryResults);
                    return ApolloApiHelpers.createGetAccounts(queryResults.results);
                });
    }

    @GetMapping("/api/statuses/{id}/liked_by")
    public Mono<List<GetAccount>> getStatusLikers(ServerWebExchange exchange, WebSession session,
            @PathVariable("id") String id, @RequestParam(required = false) String max_id,
            @RequestParam(required = false) Integer limit) {
        long requestAccountId = getMandatoryAccountId(session);
        StatusPointer statusPointer = ApolloHelpers.parseStatusPointer(id);
        return Mono
                .fromFuture(manager.getStatusLikers(requestAccountId, statusPointer.authorId, statusPointer.statusId,
                        ApolloHelpers.parseAccountId(max_id), limit))
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                .map(queryResults -> {
                    ApolloApiHelpers.setLinkHeader(exchange, queryResults);
                    return ApolloApiHelpers.createGetAccounts(queryResults.results);
                });
    }

    /*
     * User Metrics, Data, and Actions Endpoints
     * ======================================
     * - GET /api/bookmarks
     * - GET /api/likes
     * - GET /api/directory
     * ======================================
     */

    @GetMapping("/api/bookmarks")
    public Mono<List<GetStatus>> getBookmarks(ServerWebExchange exchange, WebSession session,
            @RequestParam(required = false) String max_id, @RequestParam(required = false) Integer limit) {
        long requestAccountId = getMandatoryAccountId(session);
        StatusPointer statusPointer = ApolloHelpers.parseStatusPointer(max_id);
        return Mono.fromFuture(manager.getBookmarks(requestAccountId, statusPointer, limit))
                .map(statusQueryResults -> {
                    ApolloApiHelpers.setStatusLinkHeader(exchange, statusQueryResults);
                    return ApolloApiHelpers.createGetStatuses(statusQueryResults);
                });
    }

    @GetMapping("/api/likes")
    public Mono<List<GetStatus>> getLikes(ServerWebExchange exchange, WebSession session,
            @RequestParam(required = false) String max_id, @RequestParam(required = false) Integer limit) {
        long requestAccountId = getMandatoryAccountId(session);
        StatusPointer statusPointer = ApolloHelpers.parseStatusPointer(max_id);
        return Mono.fromFuture(manager.getLikes(requestAccountId, statusPointer, limit))
                .map(statusQueryResults -> {
                    ApolloApiHelpers.setStatusLinkHeader(exchange, statusQueryResults);
                    return ApolloApiHelpers.createGetStatuses(statusQueryResults);
                });
    }

    @GetMapping("/api/directory")
    public Mono<List<GetAccount>> getDirectory(
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Integer offset,
            @RequestParam(required = false) String order,
            @RequestParam(required = false) Boolean local) {
        HashSet<String> orders = new HashSet<>(Arrays.asList(null, "active", "new"));
        if (!orders.contains(order))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        boolean showAll = local == null || !local;
        boolean sortByActive = order == null || order.equals("active");
        return Mono.fromFuture(manager.getDirectory(showAll, sortByActive, limit, offset))
                .map(ApolloApiHelpers::createGetAccounts);
    }

    /*
     * Notifications Endpoints
     * ======================================
     * - POST /api/notifications
     * - GET /api/notifications/{id}
     * - POST /api/notifications/clear
     * - GET /api/markers
     * - POST /api/markers
     * ======================================
     */

    @GetMapping("/api/notifications")
    public Mono<List<GetNotification>> getNotifications(
            ServerWebExchange exchange,
            WebSession session,
            @RequestParam(required = false) String max_id,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false, value = "types[]") List<String> types,
            @RequestParam(required = false, value = "exclude_types[]") List<String> exclude_types) {
        long requestAccountId = getMandatoryAccountId(session);
        return Mono
                .fromFuture(manager.getNotificationsTimeline(requestAccountId,
                        ApolloHelpers.parseNotificationId(max_id), limit, types, exclude_types))
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                .map(queryResults -> {
                    ApolloApiHelpers.setLinkHeader(exchange, queryResults);
                    return ApolloApiHelpers.createGetNotifications(queryResults.results);
                });
    }

    @GetMapping("/api/notifications/{id}")
    public Mono<GetNotification> getNotification(WebSession session, @PathVariable("id") String notificationId) {
        long requestAccountId = getMandatoryAccountId(session);
        return Mono
                .fromFuture(
                        manager.getNotification(requestAccountId, ApolloHelpers.parseNotificationId(notificationId)))
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                .map(GetNotification::new);
    }

    @PostMapping("/api/notifications/clear")
    public Mono dismissAllNotifications(WebSession session) {
        long requestAccountId = getMandatoryAccountId(session);
        return Mono.fromFuture(manager.dismissNotification(requestAccountId, null))
                .map(res -> new HashMap());
    }

    @GetMapping("/api/markers")
    public Mono<Map<String, GetMarker>> getMarkers(WebSession session,
            @RequestParam(value = "timeline[]", required = true) List<String> timelines) {
        long requestAccountId = getMandatoryAccountId(session);
        return Mono.fromFuture(manager.getAccountWithId(requestAccountId))
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                .map(accountWithId -> {
                    if (timelines.size() > QUERY_PARAM_ARRAY_SIZE_LIMIT)
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
                    Map<String, Marker> markers = new HashMap<>();
                    if (accountWithId.account.markers != null)
                        markers.putAll(accountWithId.account.markers);
                    return ApolloApiHelpers.createGetMarkers(markers);
                });
    }

    @PostMapping("/api/markers")
    public Mono<Map<String, GetMarker>> postMarkers(WebSession session, @RequestBody PostMarkers params) {
        long requestAccountId = getMandatoryAccountId(session);
        return Mono.fromFuture(manager.getAccountWithId(requestAccountId))
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                .flatMap(accountWithId -> {
                    // update map of markers
                    Map<String, Marker> markers = new HashMap<>();
                    if (accountWithId.account.markers != null)
                        markers.putAll(accountWithId.account.markers);
                    if (params.home != null) {
                        Marker marker = new Marker(params.home.last_read_id, 0, System.currentTimeMillis());
                        if (markers.get("home") != null)
                            marker.version = markers.get("home").version + 1;
                        markers.put("home", marker);
                    }
                    if (params.notifications != null) {
                        Marker marker = new Marker(params.notifications.last_read_id, 0, System.currentTimeMillis());
                        if (markers.get("notifications") != null)
                            marker.version = markers.get("notifications").version + 1;
                        markers.put("notifications", marker);
                    }
                    // update account
                    List<EditAccountField> edits = new ArrayList<>();
                    edits.add(EditAccountField.markers(markers));
                    return Mono.fromFuture(manager.postEditAccount(requestAccountId, edits))
                            .map(res -> ApolloApiHelpers.createGetMarkers(markers));
                });
    }

    /*
     * Poll Endpoints
     * ======================================
     * - GET /api/polls/{id}
     * - POST /api/polls/{id}/votes
     * ======================================
     */

    @GetMapping("/api/polls/{id}")
    public Mono<GetPoll> getPoll(WebSession session, @PathVariable("id") String id) {
        Long requestAccountId = (Long) session.getAttributes().get("accountId"); // allowed to be null
        StatusPointer statusPointer = ApolloHelpers.parseStatusPointer(id);
        return Mono.fromFuture(manager.getStatus(requestAccountId, statusPointer))
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                .map(statusQueryResult -> {
                    StatusResult statusResult = statusQueryResult.result.status;
                    final PollContent pollContent;
                    if (statusResult.content.isSetNormal()) {
                        NormalStatusContent content = statusResult.content.getNormal();
                        if (!statusResult.isSetPollInfo() || !content.isSetPollContent())
                            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
                        pollContent = content.pollContent;
                    } else if (statusResult.content.isSetReply()) {
                        ReplyStatusContent content = statusResult.content.getReply();
                        if (!statusResult.isSetPollInfo() || !content.isSetPollContent())
                            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
                        pollContent = content.pollContent;
                    } else
                        throw new ResponseStatusException(HttpStatus.NOT_FOUND);
                    return new GetPoll(id, statusResult.pollInfo, pollContent);
                });
    }

    @PostMapping("/api/polls/{id}/votes")
    public Mono<GetPoll> postPollVote(WebSession session, @PathVariable("id") String id,
            @RequestBody(required = true) PostPollVote params) {
        long requestAccountId = getMandatoryAccountId(session);
        String requestAccountName = (String) session.getAttributes().get("accountName");
        Set<Integer> choices = params.choices.stream().map(Integer::parseInt).collect(Collectors.toSet());
        if (choices.size() == 0 || choices.size() > QUERY_PARAM_ARRAY_SIZE_LIMIT)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        return this.getPoll(session, id)
                .flatMap(poll -> {
                    if (!poll.multiple && choices.size() > 1)
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                "Can only vote for one thing in non-multi-choice poll");
                    else if (poll.expired)
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Can't vote on an expired poll");

                    StatusPointer statusPointer = ApolloHelpers.parseStatusPointer(id);
                    return Mono.fromFuture(manager.getAccountWithIdPair(requestAccountId, statusPointer.authorId))
                            .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                            .flatMap(accountWithIdPair -> {
                                AccountWithId requester = accountWithIdPair.getKey();
                                AccountWithId author = accountWithIdPair.getValue();
                                return Mono.just(true);
                            })
                            .flatMap(result -> Mono
                                    .fromFuture(manager.postPollVote(requestAccountId, statusPointer, choices)));
                })
                .flatMap(result -> this.getPoll(session, id));
    }

    /*
     * Filter Endpoints
     * ======================================
     * - GET /api/filters
     * - POST /api/filters
     * - GET /api/filters/{id}
     * - PUT/PATCH /api/filters/{id}
     * - DELETE /api/filters/{id}
     * ======================================
     */

    @GetMapping("/api/filters")
    public Mono<List<GetFilter>> getFilters(WebSession session) {
        long requestAccountId = getMandatoryAccountId(session);
        return Mono.fromFuture(manager.getFilters(requestAccountId))
                .map(filterList -> filterList.stream().map(GetFilter::new).collect(Collectors.toList()));
    }

    @PostMapping("/api/filters")
    public Mono<GetFilter> postFilter(WebSession session, @RequestBody PostFilterParams params) {
        long requestAccountId = getMandatoryAccountId(session);
        Filter filter = new Filter();
        filter.setAccountId(requestAccountId);
        filter.setTitle(params.title);
        filter.setAction(params.parseAction());
        filter.setContexts(params.parseContexts());
        // you can't directly create a filter with statuses; they must be added later
        filter.setStatuses(new HashSet<>());

        if (params.expires_in != null && !params.expires_in.isNull()) {
            long expiresIn;
            if (params.expires_in.isTextual())
                expiresIn = Long.parseLong(params.expires_in.asText());
            else if (params.expires_in.isNumber())
                expiresIn = params.expires_in.asLong();
            else
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
            filter.setExpirationMillis(System.currentTimeMillis() + expiresIn * 1000);
        }
        filter.setKeywords(params.parseKeywordsAsCreates());

        return Mono.fromFuture(manager.postFilter(filter)).map(GetFilter::new);
    }

    @GetMapping("/api/filters/{id}")
    public Mono<GetFilter> getFilter(WebSession session, @PathVariable("id") Long filterId) {
        long requestAccountId = getMandatoryAccountId(session);
        return Mono.fromFuture(manager.getFilter(requestAccountId, filterId))
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                .map(GetFilter::new);
    }

    @RequestMapping(value = "/api/filters/{id}", method = { RequestMethod.PUT, RequestMethod.PATCH })
    public Mono<GetFilter> putFilter(WebSession session, @PathVariable("id") Long filterId,
            @RequestBody PostFilterParams params) {
        long requestAccountId = getMandatoryAccountId(session);
        EditFilter edit = (new EditFilter())
                .setAccountId(requestAccountId)
                .setFilterId(filterId)
                .setTimestamp(System.currentTimeMillis());
        if (params.title != null)
            edit.setTitle(params.title);
        if (params.context != null)
            edit.setContext(params.parseContexts());
        if (params.expires_in != null && !params.expires_in.isNull()) {
            long expiresIn;
            if (params.expires_in.isTextual())
                expiresIn = Long.parseLong(params.expires_in.asText());
            else if (params.expires_in.isNumber())
                expiresIn = params.expires_in.asLong();
            else
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
            edit.setExpirationMillis(System.currentTimeMillis() + expiresIn * 1000);
        }
        edit.setKeywords(params.parseKeywordsAsUpdates());
        if (params.filter_action != null)
            edit.setAction(params.parseAction());
        return Mono.fromFuture(manager.putFilter(edit))
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                .map(GetFilter::new);

    }

    @DeleteMapping("/api/filters/{id}")
    public Mono<Void> deleteFilter(WebSession session, @PathVariable("id") Long filterId) {
        long requestAccountId = getMandatoryAccountId(session);
        return Mono.fromFuture(manager.deleteFilter(requestAccountId, filterId));
    }

    /*
     * Media Endpoints
     * ======================================
     * - POST /api/media
     * - PUT /api/media/{id}
     * - GET /api/media/{id}
     * ======================================
     */

    @PostMapping("/api/media")
    public Mono<GetAttachment> postMedia(WebSession session, @RequestPart("file") FilePart file) throws IOException {
        long requestAccountId = getMandatoryAccountId(session);
        // determine the file type
        String ext = FilenameUtils.getExtension(file.filename()).toLowerCase();
        final String kind;
        if (ApolloApiConfig.IMAGE_EXTS.contains(ext))
            kind = "image";
        else if (ApolloApiConfig.VIDEO_EXTS.contains(ext))
            kind = "video";
        else
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Unrecognized file type");
        // transfer to static file dir
        File destDir = new File(ApolloApiConfig.STATIC_FILE_DIR, requestAccountId + "");
        destDir.mkdirs();
        String uuid = UUID.randomUUID().toString();
        File destFile = new File(destDir, String.format("%s.%s", uuid, ext));
        return file.transferTo(destFile)
                .then(Mono.just(true))
                .flatMap(res -> {
                    // validate
                    if (!ApolloApiHelpers.isValidFile(kind, destFile)) {
                        destFile.delete();
                        throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Failed to validate file");
                    }
                    String path = String.format("%s/%s.%s", requestAccountId, uuid, ext);
                    AttachmentWithId attachmentWithId = new AttachmentWithId(uuid,
                            new Attachment(ApolloApiHelpers.createAttachmentKind(kind), path, ""));
                    // upload to s3 if enabled
                    if (ApolloApiConfig.S3_OPTIONS != null) {
                        return Mono
                                .fromFuture(ApolloApiHelpers.uploadToS3(ApolloApiConfig.S3_OPTIONS.bucketName, path,
                                        destFile))
                                .map(resp -> {
                                    destFile.delete();
                                    if (resp.sdkHttpResponse().isSuccessful())
                                        return attachmentWithId;
                                    else
                                        throw new RuntimeException(
                                                resp.sdkHttpResponse().statusText().orElse("Failed to connect to S3"));
                                });
                    } else
                        return Mono.just(attachmentWithId);
                })
                .flatMap(attachmentWithId -> Mono.fromFuture(manager.postAttachment(attachmentWithId)))
                .map(GetAttachment::new);
    }

    @PutMapping(value = "/api/media/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Mono<GetAttachment> putMedia(WebSession session, @PathVariable("id") String attachmentId,
            @RequestBody(required = true) PutAttachment params) {
        long requestAccountId = getMandatoryAccountId(session);
        return Mono.fromFuture(manager.getAttachment(attachmentId))
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                .flatMap(attachmentWithId -> {
                    if (params.description != null)
                        attachmentWithId.attachment.description = params.description;
                    return Mono.fromFuture(manager.postAttachment(attachmentWithId));
                })
                .map(GetAttachment::new);
    }

    @PutMapping(value = "/api/media/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<GetAttachment> putMedia(
            WebSession session,
            @PathVariable("id") String attachmentId,
            @RequestPart(value = "description", required = false) String description) {
        PutAttachment putAttachment = new PutAttachment();
        putAttachment.description = description;
        return this.putMedia(session, attachmentId, putAttachment);
    }

    // TODO: Idk if this works
    @GetMapping("/api/media/{id}")
    public Mono<GetAttachment> getMedia(@PathVariable("id") String attachmentId) {
        return Mono.fromFuture(manager.getAttachment(attachmentId))
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                .map(GetAttachment::new);
    }

    /*
     * Suggestions Endpoints
     * ======================================
     * - GET /api/suggestions
     * - DELETE /api/suggestions/{id}
     * ======================================
     */

    @GetMapping("/api/suggestions")
    public Mono<List<GetSuggestion>> getSuggestions(WebSession session) {
        long requestAccountId = getMandatoryAccountId(session);
        return Mono.fromFuture(manager.getWhoToFollowSuggestions(requestAccountId))
                .map(accountWithIds -> accountWithIds.stream().map(a -> new GetSuggestion("global", a))
                        .collect(Collectors.toList()));
    }

    @DeleteMapping("/api/suggestions/{id}")
    public Mono deleteSuggestion(WebSession session, @PathVariable("id") String id) {
        long requestAccountId = getMandatoryAccountId(session);
        return Mono.fromFuture(manager.removeFollowSuggestion(requestAccountId, ApolloHelpers.parseAccountId(id)))
                .map(res -> new HashMap());
    }

    /*
     * Tags Endpoints
     * ======================================
     * - GET /api/tags/{id}
     * - POST /api/tags/{id}/follow
     * - POST /api/tags/{id}/follow
     * - GET /api/followed_tags
     * ======================================
     */

    @GetMapping("/api/tags/{id}")
    public Mono<GetTag> getTag(WebSession session, @PathVariable("id") String id) {
        Long requestAccountId = (Long) session.getAttributes().get("accountId"); // allowed to be null
        return Mono.fromFuture(manager.getHashtagStats(id))
                .flatMap(stats -> (requestAccountId == null ? Mono.just(false)
                        : Mono.fromFuture(manager.isFollowingHashtag(requestAccountId, id)))
                        .map(isFollowing -> ApolloApiHelpers.createGetTag(id, stats, isFollowing)));
    }

    @PostMapping("/api/tags/{id}/follow")
    public Mono<GetTag> postFollowTag(WebSession session, @PathVariable("id") String id) {
        long requestAccountId = getMandatoryAccountId(session);
        return Mono.fromFuture(manager.postFollowHashtag(requestAccountId, id))
                .flatMap(res -> this.getTag(session, id));
    }

    @PostMapping("/api/tags/{id}/unfollow")
    public Mono<GetTag> postUnfollowTag(WebSession session, @PathVariable("id") String id) {
        long requestAccountId = getMandatoryAccountId(session);
        return Mono.fromFuture(manager.postRemoveFollowHashtag(requestAccountId, id))
                .flatMap(res -> this.getTag(session, id));
    }

    @GetMapping("/api/followed_tags")
    public Mono<List<GetTag>> getFollowedTags(WebSession session) {
        Long requestAccountId = (Long) session.getAttributes().get("accountId");
        if (requestAccountId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }

        return Mono.fromFuture(manager.getFollowedHashtags(requestAccountId))
                .flatMap(followedSet -> {
                    List<CompletableFuture<GetTag>> futures = followedSet.stream()
                            .map(hashtag -> manager.getHashtagStats(hashtag)
                                    .thenApply(stats -> ApolloApiHelpers.createGetTag(hashtag, stats, true)))
                            .collect(Collectors.toList());

                    return Mono.fromFuture(
                            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                                    .thenApply(ignored -> futures.stream()
                                            .map(CompletableFuture::join)
                                            .collect(Collectors.toList())));
                });
    }

    /*
     * Spaces Endpoints
     * ======================================
     * - GET /api/spaces
     * - GET /api/spaces/{id}
     * - POST /api/spaces/{id}/follow
     * - POST /api/spaces/{id}/follow
     * - GET /api/followed_spaces
     * ======================================
     */

    @GetMapping("/api/spaces")
    public Mono<List<GetSpace>> getAllSpaces(WebSession session) {
        return Mono.fromFuture(manager.getAllSpaces())
                .flatMap(allSpaces -> {
                    Long requestAccountId = (Long) session.getAttributes().get("accountId");
                    if (requestAccountId == null) {
                        return Mono.just(
                                allSpaces.stream()
                                        .map(space -> new GetSpace(space.id, space.name))
                                        .collect(Collectors.toList()));
                    }

                    return Mono.fromFuture(manager.getFollowedSpaceIds(requestAccountId))
                            .onErrorResume(e -> {
                                // Log the error but continue with empty set
                                logger.error("Error fetching followed spaces", e);
                                return Mono.just(Collections.emptySet());
                            })
                            .defaultIfEmpty(Collections.emptySet())
                            .map(userFollowedSet -> {
                                return allSpaces.stream()
                                        .map(space -> {
                                            GetSpace getSpace = new GetSpace(space.id, space.name);
                                            getSpace.following = userFollowedSet.contains(space.id);
                                            return getSpace;
                                        })
                                        .collect(Collectors.toList());
                            });
                });
    }

    @GetMapping("/api/spaces/{id}")
    public Mono<GetSpace> getSpace(WebSession session, @PathVariable("id") String id) {
        Long requestAccountId = (Long) session.getAttributes().get("accountId");
        return Mono.fromFuture(manager.getSpaceFromSpaceId(id))
                .flatMap(space -> {
                    if (space == null) {
                        return Mono.empty();
                    }
                    return Mono.fromFuture(manager.getSpaceStats(id))
                            .flatMap(stats -> (requestAccountId == null ? Mono.just(false)
                                    : Mono.fromFuture(manager.isFollowingSpace(requestAccountId, id)))
                                    .map(isFollowing -> ApolloApiHelpers.createGetSpace(id,
                                            ApolloApiHelpers.getSpaceNameFromId(id), stats, isFollowing)));
                });
    }

    @PostMapping("/api/spaces/{id}/follow")
    public Mono<GetSpace> postFollowSpace(WebSession session, @PathVariable("id") String id) {
        long requestAccountId = getMandatoryAccountId(session);
        return Mono.fromFuture(manager.postFollowSpace(requestAccountId, id))
                .flatMap(res -> this.getSpace(session, id));
    }

    @PostMapping("/api/spaces/{id}/unfollow")
    public Mono<GetSpace> postUnfollowSpace(WebSession session, @PathVariable("id") String id) {
        long requestAccountId = getMandatoryAccountId(session);
        return Mono.fromFuture(manager.postRemoveFollowSpace(requestAccountId, id))
                .flatMap(res -> this.getSpace(session, id));
    }

    /*
     * Timeline Endpoints
     * ======================================
     * - GET /api/timelines/home
     * - GET /api/timelines/direct
     * - GET /api/timelines/public
     * - GET /api/timelines/tag/{hashtag}
     * - GET /api/timelines/space/{space}
     * ======================================
     */

    @GetMapping("/api/timelines/home")
    public Mono<List<GetStatus>> getHomeTimeline(WebSession session, ServerWebExchange exchange,
            @RequestParam(required = false) String max_id, @RequestParam(required = false) Integer limit) {
        long requestAccountId = getMandatoryAccountId(session);
        StatusPointer statusPointer = ApolloHelpers.parseStatusPointer(max_id);
        return Mono.fromFuture(manager.getHomeTimeline(requestAccountId, statusPointer, limit))
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                .map(statusQueryResults -> {
                    ApolloApiHelpers.setStatusLinkHeader(exchange, statusQueryResults);
                    return ApolloApiHelpers.createGetStatuses(statusQueryResults);
                });
    }

    @GetMapping("/api/timelines/direct")
    public Mono<List<GetStatus>> getDirectTimeline(WebSession session, ServerWebExchange exchange,
            @RequestParam(required = false) String max_id, @RequestParam(required = false) Integer limit) {
        long requestAccountId = getMandatoryAccountId(session);
        StatusPointer statusPointer = ApolloHelpers.parseStatusPointer(max_id);
        return Mono.fromFuture(manager.getDirectTimeline(requestAccountId, statusPointer, limit))
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                .map(statusQueryResults -> {
                    ApolloApiHelpers.setStatusLinkHeader(exchange, statusQueryResults);
                    return ApolloApiHelpers.createGetStatuses(statusQueryResults);
                });
    }

    @GetMapping("/api/timelines/public")
    public Mono<List<GetStatus>> getPublicTimeline(WebSession session, ServerWebExchange exchange,
            @RequestParam(required = false) String max_id,
            @RequestParam(required = false) Integer limit) {
        Long requestAccountId = (Long) session.getAttributes().get("accountId"); // allowed to be null
        StatusPointer statusPointer = ApolloHelpers.parseStatusPointer(max_id);

        // As only the default Public option is used, we directly use
        // LocalTimeline.Public
        final LocalTimeline timeline = LocalTimeline.Public;

        return Mono.fromFuture(manager.getLocalTimeline(timeline, requestAccountId, statusPointer, limit))
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                .map(statusQueryResults -> {
                    ApolloApiHelpers.setStatusLinkHeader(exchange, statusQueryResults);
                    return ApolloApiHelpers.createGetStatuses(statusQueryResults);
                });
    }

    @GetMapping("/api/timelines/tag/{hashtag}")
    public Mono<List<GetStatus>> getHashtagTimeline(WebSession session, ServerWebExchange exchange,
            @PathVariable("hashtag") String hashtag, @RequestParam(required = false) String max_id,
            @RequestParam(required = false) Integer limit) {
        Long requestAccountId = (Long) session.getAttributes().get("accountId"); // allowed to be null
        StatusPointer statusPointer = ApolloHelpers.parseStatusPointer(max_id);
        return Mono.fromFuture(manager.getHashtagTimeline(hashtag, requestAccountId, statusPointer, limit))
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                .map(statusQueryResults -> {
                    ApolloApiHelpers.setStatusLinkHeader(exchange, statusQueryResults);
                    return ApolloApiHelpers.createGetStatuses(statusQueryResults);
                });
    }

    @GetMapping("/api/timelines/space/{space}")
    public Mono<List<GetStatus>> getSpaceTimeline(WebSession session, ServerWebExchange exchange,
            @PathVariable("space") String space, @RequestParam(required = false) String max_id,
            @RequestParam(required = false) Integer limit) {
        Long requestAccountId = (Long) session.getAttributes().get("accountId"); // allowed to be null
        StatusPointer statusPointer = ApolloHelpers.parseStatusPointer(max_id);
        return Mono.fromFuture(manager.getSpaceTimeline(space, requestAccountId, statusPointer, limit))
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                .map(statusQueryResults -> {
                    ApolloApiHelpers.setStatusLinkHeader(exchange, statusQueryResults);
                    return ApolloApiHelpers.createGetStatuses(statusQueryResults);
                });
    }

    /*
     * Search Endpoints
     * ======================================
     * - GET /api/search
     * ======================================
     */

    @GetMapping("/api/search")
    public Mono<GetSearch> getSearch(
            WebSession session,
            ServerWebExchange exchange,
            @RequestParam(required = true) String q,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Boolean resolve,
            @RequestParam(required = false) Boolean following,
            @RequestParam(required = false) String account_id,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Long offset,
            @RequestParam(required = false) Long start_next_id,
            @RequestParam(required = false) String start_term) throws MalformedURLException {
        long requestAccountId = getMandatoryAccountId(session);
        List<String> terms = Arrays.asList(q.toLowerCase().trim().split("\\s+"));
        Map startParams = ApolloApiHelpers.createSearchParams(start_next_id, start_term);

        return Mono.zip(
                // Account search
                Mono.fromFuture((type == null || type.equals("accounts")) && (offset == null || offset == 0L)
                        ? manager.getProfileSearch(requestAccountId, terms, startParams, limit,
                                following != null && following)
                        : CompletableFuture.completedFuture(
                                new ApolloApiManager.QueryResults<AccountWithId, Map>(new ArrayList<>(), true, null,
                                        null))),
                // Status search
                Mono.fromFuture((type == null || type.equals("statuses")) && (offset == null || offset == 0L)
                        ? manager.getStatusSearch(requestAccountId, ApolloHelpers.parseAccountId(account_id), terms,
                                startParams, limit)
                        : CompletableFuture.completedFuture(new ApolloApiManager.QueryResults<StatusQueryResult, Map>(
                                new ArrayList<>(), true, null, null))),
                // Hashtag search
                Mono.fromFuture((type == null || type.equals("hashtags")) && (offset == null || offset == 0L)
                        ? manager.getHashtagSearch(terms.get(0), startParams, limit)
                        : CompletableFuture
                                .completedFuture(new ApolloApiManager.QueryResults<SimpleEntry<String, ItemStats>, Map>(
                                        new ArrayList<>(), true, null, null))),
                // Space search
                Mono.fromFuture((type == null || type.equals("spaces")) && (offset == null || offset == 0L)
                        ? manager.getSpaceSearch(terms.get(0), startParams, limit)
                        : CompletableFuture
                                .completedFuture(new ApolloApiManager.QueryResults<SimpleEntry<String, ItemStats>, Map>(
                                        new ArrayList<>(), true, null, null))))
                .map(results -> {
                    ApolloApiManager.QueryResults<AccountWithId, Map> accounts = results.getT1();
                    ApolloApiManager.QueryResults<StatusQueryResult, Map> statuses = results.getT2();
                    ApolloApiManager.QueryResults<SimpleEntry<String, ItemStats>, Map> hashtags = results.getT3();
                    ApolloApiManager.QueryResults<SimpleEntry<String, ItemStats>, Map> spaces = results.getT4();

                    if ("accounts".equals(type))
                        ApolloApiHelpers.setLinkHeader(exchange, accounts);
                    else if ("statuses".equals(type))
                        ApolloApiHelpers.setLinkHeader(exchange, statuses);
                    else if ("hashtags".equals(type))
                        ApolloApiHelpers.setLinkHeader(exchange, hashtags);
                    else if ("spaces".equals(type))
                        ApolloApiHelpers.setLinkHeader(exchange, spaces);

                    return new GetSearch(
                            ApolloApiHelpers.createGetAccounts(accounts.results),
                            ApolloApiHelpers.createGetStatuses(statuses.results),
                            ApolloApiHelpers.createGetTags(hashtags.results),
                            ApolloApiHelpers.createGetSpaces(spaces.results));
                });
    }

    /*
     * Instance Endpoints
     * ======================================
     * - GET /api/instance/rules
     * ======================================
     */

    // TODO: This just returns empty list, so maybe need to do something, but it
    // might be handled on frontend
    @GetMapping("/api/instance/rules")
    public Mono<List<GetRule>> getInstanceRules() {
        return Mono.just(new ArrayList<>());
    }

    // General Endpoints

    @GetMapping("/api/teams/{id}")
    public Mono<GetTeam> getTeam(@PathVariable("id") int teamId) {
        return Mono.fromFuture(manager.getTeam(teamId))
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Team not found")));
    }

    /*
     * League of Legends Series Endpoints
     * ======================================
     * - GET /api/lolseries/{id}
     * - GET /api/lolseries/schedule
     * - GET /api/lolseries/week
     * ======================================
     */

    @GetMapping("/api/lol/series/{id}")
    public Mono<GetSeries> getLolSeries(@PathVariable("id") int seriesId) {
        return Mono.fromFuture(manager.getSeries(seriesId))
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)));
    }

    @GetMapping("/api/lol/series/schedule")
    public Mono<List<GetSeries>> getLolSeriesSchedule(
            @RequestParam double startTime,
            @RequestParam double endTime) {
        logger.info("Fetching series schedule from {} to {}", startTime, endTime);
        long startMillis = (long) (startTime * 1000);
        long endMillis = (long) (endTime * 1000);
        return Mono.fromFuture(manager.getSeriesSchedule(startMillis, endMillis))
                .doOnError(e -> logger.error("Error fetching series schedule: ", e))
                .onErrorResume(e -> {
                    logger.error("Error processing series schedule request", e);
                    return Mono.error(new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                            "Error fetching series schedule", e));
                });
    }

    @GetMapping("/api/lol/series/week")
    public Mono<List<GetSeries>> getLolSeriesWeekSchedule(
            @RequestParam(required = false) Double timestamp) {
        logger.info("Fetching week schedule for timestamp: {}", timestamp);
        long timeMillis = (timestamp != null) ? (long) (timestamp * 1000) : System.currentTimeMillis();
        return Mono.fromFuture(manager.getWeekSchedule(timeMillis))
                .doOnError(e -> logger.error("Error fetching week schedule: ", e))
                .onErrorResume(e -> {
                    logger.error("Error processing week schedule request", e);
                    return Mono.error(new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                            "Error fetching week schedule", e));
                });
    }

    /*
     * League of Legends Match Endpoints
     * ======================================
     * - GET /api/lolmatches/{id}
     * ======================================
     */

    @GetMapping("/api/matches/{id}")
    public Mono<GetMatch> getLolMatch(@PathVariable("id") int matchId) {
        return Mono.fromFuture(manager.getMatch(matchId))
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)));
    }

    /*
     * League of Legends Team Endpoints
     * ======================================
     * - GET /api/lol/teams/{id}
     * ======================================
     */

    @GetMapping("/api/lol/teams/{id}")
    public Mono<GetTeam> getLolTeamWithStats(@PathVariable("id") int teamId) {
        return Mono.fromFuture(manager.getTeamWithLolStats(teamId))
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Team not found")));
    }

    @GetMapping("/api/lol/teams")
    public Mono<List<GetTeam>> getAllLolTeamsWithAggStats() {
        return Mono.fromFuture(manager.getAllTeamsWithAggStats())
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "No teams found")));
    }

    /*
     * League of Legends Player Endpoints
     * ======================================
     * - GET /api/lolplayers/{id}
     * ======================================
     */

    @GetMapping("/api/lol/players/{id}")
    public Mono<GetPlayer> getLolPlayerWithStats(@PathVariable("id") int playerId) {
        return Mono.fromFuture(manager.getPlayerWithLolStats(playerId))
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Player not found")));
    }

    @GetMapping("/api/lol/players")
    public Mono<List<GetPlayer>> getAllLolPlayersWithAggStats() {
        return Mono.fromFuture(manager.getAllPlayersWithAggStats())
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "No players found")));
    }

    @GetMapping("/api/rosters/{rosterId}/players")
    public Mono<ResponseEntity<List<GetPlayer>>> getPlayersByRosterId(@PathVariable int rosterId) {
        return Mono.fromFuture(manager.getPlayersFromRosterId(rosterId))
                .map(players -> ResponseEntity.ok(players))
                .onErrorResume(ResponseStatusException.class, e -> {
                    // Handle 404 Not Found for roster not found
                    if (e.getStatus() == HttpStatus.NOT_FOUND) {
                        return Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND).build());
                    }
                    // Handle other ResponseStatusExceptions if needed
                    return Mono.just(ResponseEntity.status(e.getStatus()).build());
                })
                .onErrorResume(e -> {
                    // Handle any other unexpected exceptions
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
                });
    }

    /*
     * Lol Match Summary Endpoints
     * ======================================
     * - GET /api/lolmatches/{id}/summary
     * ======================================
     */

    @GetMapping("/api/lol/matches/{id}/summary")
    public Mono<GetLolMatchSummary> getLolMatchSummary(@PathVariable("id") int matchId) {
        return Mono.fromFuture(manager.getLolMatchSummary(matchId))
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)));
    }

    /*
     * Lol Assets Endpoints
     * ======================================
     * - GET /api/lolassets/{id}/
     * ======================================
     */

    @GetMapping("/api/lol/assets/{id}")
    public Mono<GetAsset> getLolAsset(@PathVariable("id") int assetId) {
        return Mono.fromFuture(manager.getAsset(assetId))
                .map(asset -> new GetAsset(asset))
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Asset not found")));
    }

    /*
     * Admin Endpoints
     * ======================================
     * - GET /api/admin/accounts
     * ======================================
     */

    @GetMapping("/api/admin/accounts")
    public Mono<List<GetAccount>> getAllAdminAccounts(WebSession session, ServerHttpResponse response) {
        logger.info("getAllAdminAccounts - Fetching all accounts");
        return Mono.fromFuture(manager.getAllAccounts())
                .flatMap(accounts -> {
                    if (accounts == null || accounts.isEmpty()) {
                        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No accounts found");
                    }
                    // Convert to GetAccount
                    List<GetAccount> responseBody = accounts.stream()
                            .map(GetAccount::new)
                            .collect(Collectors.toList());
                    return Mono.just(responseBody);
                })
                .onErrorResume(ex -> {
                    throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not fetch accounts", ex);
                });
    }

    @PostMapping("/api/admin/users/permission_group/{group}")
    public Mono<Void> addPermissionGroup(
            @PathVariable String group,
            @RequestBody List<String> nicknames,
            WebSession session,
            ServerHttpResponse response) {
        logger.info("addPermissionGroup - Adding users {} to group {}", nicknames, group);
        return Mono.fromFuture(manager.addPermissionGroup(nicknames, group))
                .then()
                .onErrorResume(ex -> {
                    throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                            "Could not add permission group", ex);
                });
    }

    @DeleteMapping("/api/admin/users/permission_group/{group}")
    public Mono<Void> removePermissionGroup(
            @PathVariable String group,
            @RequestBody List<String> nicknames,
            WebSession session,
            ServerHttpResponse response) {
        logger.info("removePermissionGroup - Removing users {} from group {}", nicknames, group);
        return Mono.fromFuture(manager.removePermissionGroup(nicknames, group))
                .then()
                .onErrorResume(ex -> {
                    throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                            "Could not remove permission group", ex);
                });
    }

    @PutMapping("/api/admin/users/tag")
    public Mono<Void> addUserTags(
            @RequestBody Map<String, Object> request,
            WebSession session,
            ServerHttpResponse response) {
        List<String> nicknames = (List<String>) request.get("nicknames");
        List<String> tags = (List<String>) request.get("tags");

        logger.info("addUserTags - Adding tags {} to users {}", tags, nicknames);
        return Mono.fromFuture(manager.addUserTags(nicknames, tags))
                .then()
                .onErrorResume(ex -> {
                    throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                            "Could not add tags", ex);
                });
    }

    @DeleteMapping("/api/admin/users/tag")
    public Mono<Void> removeUserTags(
            @RequestBody Map<String, Object> request,
            WebSession session,
            ServerHttpResponse response) {
        List<String> nicknames = (List<String>) request.get("nicknames");
        List<String> tags = (List<String>) request.get("tags");

        logger.info("removeUserTags - Removing tags {} from users {}", tags, nicknames);
        return Mono.fromFuture(manager.removeUserTags(nicknames, tags))
                .then()
                .onErrorResume(ex -> {
                    throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                            "Could not remove tags", ex);
                });
    }

    @PatchMapping("/api/admin/users/suggest")
    public Mono<Void> suggestUsers(
            @RequestBody Map<String, Object> request,
            WebSession session,
            ServerHttpResponse response) {
        List<String> nicknames = (List<String>) request.get("nicknames");
        logger.info("suggestUsers - Setting users {} as suggested", nicknames);
        return Mono.fromFuture(manager.setSuggestedUsers(nicknames, true))
                .then()
                .onErrorResume(ex -> {
                    throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                            "Could not suggest users", ex);
                });
    }

    @PatchMapping("/api/admin/users/unsuggest")
    public Mono<Void> unsuggestUsers(
            @RequestBody Map<String, Object> request,
            WebSession session,
            ServerHttpResponse response) {
        List<String> nicknames = (List<String>) request.get("nicknames");
        logger.info("unsuggestUsers - Removing suggested status from users {}", nicknames);
        return Mono.fromFuture(manager.setSuggestedUsers(nicknames, false))
                .then()
                .onErrorResume(ex -> {
                    throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                            "Could not unsuggest users", ex);
                });
    }

    @PostMapping("/api/reports")
    public Mono<Void> submitReport(WebSession session, @RequestBody PostReport params) {
        long reporterAccountId = getMandatoryAccountId(session);
        return Mono.fromFuture(manager.getAccountWithId(null, ApolloHelpers.parseAccountId(params.account_id)))
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                .flatMap(targetAccount -> Mono.fromFuture(manager.saveReport(params, reporterAccountId, targetAccount)))
                .then();
    }

    @GetMapping("/api/admin/reports")
    public Mono<List<GetReport>> getReports(@RequestParam Map<String, String> params, WebSession session) {
        return Mono.fromFuture(manager.getReports(params))
                .flatMap(rawReports -> {
                    List<Mono<GetReport>> enrichedReports = rawReports.stream()
                            .map(report -> {
                                // Fetch reporter account
                                Mono<AccountWithId> reporterAccount = Mono.fromFuture(
                                        manager.getAccountWithId(null, report.getReporter_account_id()));
                                // Fetch target account
                                Mono<AccountWithId> targetAccount = Mono.fromFuture(
                                        manager.getAccountWithId(null, report.getTarget_account_id()));
                                // Combine them
                                return Mono.zip(reporterAccount, targetAccount)
                                        .map(tuple -> {
                                            GetReport getReport = new GetReport(report);

                                            // Create account Map
                                            Map<String, Object> reporterAccountMap = new HashMap<>();
                                            reporterAccountMap.put("account", new HashMap<String, Object>() {
                                                {
                                                    put("id",
                                                            ApolloHelpers.serializeAccountId(tuple.getT1().accountId));
                                                }
                                            });
                                            getReport.account = reporterAccountMap;

                                            // Create target account Map
                                            Map<String, Object> targetAccountMap = new HashMap<>();
                                            targetAccountMap.put("account", new HashMap<String, Object>() {
                                                {
                                                    put("id",
                                                            ApolloHelpers.serializeAccountId(tuple.getT2().accountId));
                                                }
                                            });
                                            getReport.target_account = targetAccountMap;

                                            return getReport;
                                        });
                            })
                            .collect(Collectors.toList());
                    return Mono.zip(enrichedReports, resultArray -> Arrays.stream(resultArray)
                            .map(obj -> (GetReport) obj)
                            .collect(Collectors.toList()));
                });
    }

    @PostMapping("/api/admin/reports/{id}/{action}")
    public Mono<Void> updateReportState(
            @PathVariable String id,
            @PathVariable String action,
            WebSession session) {
        if (!action.equals("resolve") && !action.equals("reopen")) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid action"));
        }
        String newState = action.equals("resolve") ? "resolved" : "open";
        return Mono.fromFuture(manager.updateReportState(id, newState))
                .onErrorResume(ex -> {
                    throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not update report state",
                            ex);
                });
    }

    @PostMapping("/api/admin/accounts/{accountId}/action")
    public Mono<Void> deactivateAccount(
            @PathVariable String accountId,
            @RequestBody Map<String, String> params,
            WebSession session) {
        if (!"disable".equals(params.get("type"))) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Invalid action type"));
        }
        String reportId = params.get("report_id");
        return Mono.fromFuture(manager.deactivateAccount(ApolloHelpers.parseAccountId(accountId), reportId))
                .onErrorResume(ex -> {
                    throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                            "Could not deactivate account", ex);
                });
    }

    @DeleteMapping("/api/admin/users")
    public Mono<String> deleteUser(
            @RequestBody Map<String, String> request,
            WebSession session,
            ServerHttpResponse response) {
        String accountId = request.get("accountId");
        logger.info("deleteUser - Deleting user {}", accountId);
        return Mono.fromFuture(manager.deleteAccount(ApolloHelpers.parseAccountId(accountId)))
                .map(success -> success ? "User deleted successfully" : "Failed to delete user")
                .onErrorResume(ex -> {
                    throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                            "Could not delete user");
                });
    }

    @DeleteMapping("/api/admin/statuses/{id}")
    public Mono<GetStatus> adminDeleteStatus(@PathVariable("id") String id) {
        StatusPointer pointer = ApolloHelpers.parseStatusPointer(id);
        return Mono.fromFuture(manager.deleteStatusInternal(pointer));
    }

    @GetMapping("/api/admin/instance")
    public Mono<GetInstanceStats> getInstanceStats() {
        return Mono.fromFuture(manager.getInstanceStats());
    }

    @PostMapping("/api/admin/track-activity")
    public Mono<Boolean> trackActivity(@RequestBody PostUserActivity activity) {
        return Mono.fromFuture(manager.storeUserActivity(activity));
    }

    @PostMapping("/api/admin/spaces")
    public Mono<Void> postSpace(WebSession session, @RequestBody PostSpace params) {
        return Mono.fromFuture(manager.saveSpace(params)).then();
    }

    @GetMapping("/api/metrics")
    public Mono<GetMetrics> getMetrics() {
        return Mono.just(new GetMetrics(ApolloApiMetrics.HOURLY_METRICS));
    }

    @GetMapping("/")
    public ResponseEntity<String> rootCheck() {
        return ResponseEntity.ok("OK");
    }

    @PostMapping("/api/change_password")
    public Mono<GetAccount> changePassword(
            WebSession session,
            @RequestBody Map<String, String> request) {

        long requestAccountId = getMandatoryAccountId(session);

        String currentPassword = request.get("password");
        String newPassword = request.get("new_password");
        String confirmation = request.get("new_password_confirmation");

        return Mono.fromFuture(manager.getAccountWithId(requestAccountId))
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                .flatMap(accountWithId -> {
                    // Verify old password matches using the correct method
                    if (!ApolloApiHelpers.matchesPassword(currentPassword, accountWithId.account.pwdHash)) {
                        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid current password");
                    }

                    // Verify new password and confirmation match
                    if (!newPassword.equals(confirmation)) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                "New password and confirmation do not match");
                    }

                    // Create new password hash using the correct method
                    String newPwdHash = ApolloApiHelpers.encodePassword(newPassword);

                    // Update account with new password
                    List<EditAccountField> edits = new ArrayList<>();
                    edits.add(EditAccountField.pwdHash(newPwdHash));

                    return Mono.fromFuture(manager.postEditAccount(requestAccountId, edits));
                })
                .flatMap(result -> Mono.fromFuture(manager.getAccountWithId(requestAccountId)))
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                .map(GetAccount::new);
    }

    @PostMapping("/api/change_email")
    public Mono<GetAccount> changeEmail(
            WebSession session,
            @RequestBody Map<String, String> request) {

        long requestAccountId = getMandatoryAccountId(session);

        String newEmail = request.get("email");
        String password = request.get("password");

        return Mono.fromFuture(manager.getAccountWithId(requestAccountId))
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                .flatMap(accountWithId -> {
                    // Verify password using the correct method
                    if (!ApolloApiHelpers.matchesPassword(password, accountWithId.account.pwdHash)) {
                        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid password");
                    }

                    // Check if email already exists
                    return Mono.fromFuture(manager.emailExists(newEmail))
                            .flatMap(exists -> {
                                if (exists) {
                                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already in use");
                                }

                                // Update account with new email
                                List<EditAccountField> edits = new ArrayList<>();
                                edits.add(EditAccountField.email(newEmail));

                                return Mono.fromFuture(manager.postEditAccount(requestAccountId, edits));
                            });
                })
                .flatMap(result -> Mono.fromFuture(manager.getAccountWithId(requestAccountId)))
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                .map(GetAccount::new);
    }

    @PostMapping("/api/delete_account")
    public Mono<Map<String, String>> deleteAccount(
            WebSession session,
            @RequestBody Map<String, String> request) {
        long requestAccountId = getMandatoryAccountId(session);
        String password = request.get("password");

        return Mono.fromFuture(manager.getAccountWithId(requestAccountId))
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                .flatMap(accountWithId -> {
                    if (!ApolloApiHelpers.matchesPassword(password, accountWithId.account.pwdHash)) {
                        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid password");
                    }
                    return Mono.fromFuture(manager.deleteAccount(requestAccountId));
                })
                .map(result -> {
                    if (!result) {
                        throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to delete account");
                    }
                    return Map.of("status", "success", "message", "Account deleted successfully");
                });
    }

}
