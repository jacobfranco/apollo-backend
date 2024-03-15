package com.apollo.backendapi;

import com.apollo.backend.data.AccountWithId;
import com.apollo.backendapi.pojos.*;

import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.*;
import reactor.core.publisher.Mono;

import java.io.*;
import java.util.*;
import java.nio.file.Files;
import java.security.NoSuchAlgorithmException;


// TODO: Implement

@RestController
@CrossOrigin(exposedHeaders = {"Link"})
public class ApolloApiController {

    public static ApolloApiManager manager;

    /*
     *  Login Function
     */
    private Mono<GetToken> loginWithAccount(WebSession session, String scope, AccountWithId accountWithId) {
        // update session
        session.getAttributes().put("accountId", accountWithId.accountId);
        session.getAttributes().put("accountName", accountWithId.account.displayName);
        // store the session id in the backend and return token
        return Mono.fromFuture(manager.postAuthCode(accountWithId.accountId, session.getId())).map(res -> new GetToken(session.getId(), scope));
    }

 /**
 * Handle creation of a new OAuth application for the Apollo API.
 * Generates a random client_secret and returns a GetApplication object with the provided redirect_uri.
 * Accepts JSON or URL-encoded request bodies.
 */
    @PostMapping(value = "/api/apps", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Mono<GetApplication> postApplication(@RequestBody(required = true) PostApplication params) throws NoSuchAlgorithmException {
        // currently, the application isn't saved anywhere 
        // TODO: Securely store secret in AWS - will need to refactor other functions
        GetApplication app = new GetApplication();
        app.redirect_uri = params.redirect_uris;
        app.client_secret = "secret_" + ApolloApiHelpers.randomString(16);
        return Mono.just(app);
    }

    @PostMapping(value = "/api/apps", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public Mono<GetApplication> postApplication(ServerWebExchange exchange) {
        return exchange.getFormData()
                       .flatMap(formParams -> {
                           PostApplication params = ApolloApiFormParser.parseParams(formParams, new PostApplication());
                           try {
                               return this.postApplication(params);
                           } catch (NoSuchAlgorithmException e) {
                               throw new RuntimeException(e);
                           }
                       });
    }

    /* TODO: Handles External Auth, can probably remove
     * 
     * 
     */
    /* 
    @GetMapping(value = "/oauth/authorize", produces = MediaType.TEXT_HTML_VALUE)
    public Mono<String> getOauthAuthorize(@RequestParam(required = true) String client_id,
                                          @RequestParam(required = true) String redirect_uri,
                                          @RequestParam(required = true) String response_type,
                                          @RequestParam(required = true) String scope) throws IOException {
        File htmlFile = new ClassPathResource("public/authorize.html").getFile();
        String htmlContent = new String(Files.readAllBytes(htmlFile.toPath()));
        htmlContent = String.format(htmlContent, client_id, redirect_uri, scope);
        return Mono.just(htmlContent);
    }

@PostMapping(value = "/oauth/authorize", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public Mono postOauthAuthorize(ServerWebExchange exchange) throws NoSuchAlgorithmException {
        String code = ApolloApiHelpers.randomString(32);
        return exchange.getFormData()
                       // parse the params
                       .map(formParams -> {
                           PostOauthAuthorize params = ApolloApiFormParser.parseParams(formParams, new PostOauthAuthorize());
                           if (params.redirect_uri == null || params.scope == null) {
                               exchange.getResponse().setStatusCode(HttpStatus.BAD_REQUEST);
                               return null;
                           } else {
                               exchange.getResponse().setStatusCode(HttpStatus.FOUND);
                               exchange.getResponse().getHeaders().set("Location", params.redirect_uri + "?code=" + code);
                               return params;
                           }
                       })
                       .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST)))
                       .flatMap(params ->
                                Mono.fromFuture(manager.getAccountId(params.username))
                                    .switchIfEmpty(Mono.just(-1L))
                                    .map(accountId -> {
                                        if (accountId == -1L) {
                                            // redirect browser clients to error page
                                            if (exchange.getRequest().getHeaders().getAccept().contains(MediaType.TEXT_HTML)) {
                                                exchange.getResponse().getHeaders().set("Location", "/auth/error");
                                                throw new ResponseStatusException(HttpStatus.FOUND);
                                            }
                                        }
                                        return accountId;
                                    })
                                    .flatMap(accountId -> Mono.fromFuture(manager.getAccountWithId(accountId)))
                                    .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Username not found")))
                                    // update the session
                                    .flatMap(accountWithId -> {
                                        // TODO: See if removing remote acc stuff messed anything up
                                        if (!ApolloApiHelpers.matchesPassword(params.password, accountWithId.account.pwdHash)) {
                                            // redirect browser clients to error page
                                            if (exchange.getRequest().getHeaders().getAccept().contains(MediaType.TEXT_HTML)) {
                                                exchange.getResponse().getHeaders().set("Location", "/auth/error");
                                                throw new ResponseStatusException(HttpStatus.FOUND);
                                            } else {
                                                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Password does not match");
                                            }
                                        }
                                        return Mono.fromFuture(manager.postAuthCode(accountWithId.accountId, code));
                                    })
                                    .map(res -> new HashMap()));
    }

     */

     /**
 * Handles OAuth token requests for various grant types including password, client credentials,
 * and authorization code. This endpoint is designed to accept both JSON and form URL-encoded data formats,
 * supporting diverse client needs. Upon receiving a request, it processes according to the specified grant type:
 * - For 'password' grant type, it verifies the user's username and password, issuing a token upon successful authentication.
 * - For 'client_credentials' grant, it generates a token based on the client's credentials without requiring a user context.
 * - For 'authorization_code' grant type, it exchanges an authorization code for a token, validating the code before issuing the token.
 * If the grant type is unsupported or not provided, it returns a bad request error.
 * This dual-method approach allows flexibility in client implementations while ensuring secure token generation and management.
 */

     @PostMapping(value = "/oauth/token", consumes = MediaType.APPLICATION_JSON_VALUE)
     public Mono<GetToken> postOauthToken(WebSession session, @RequestBody(required = true) PostToken params) {
         if ("password".equals(params.grant_type)) {
             return Mono.fromFuture(manager.getAccountId(params.username))
                        .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Username not found")))
                        // get the account
                        .flatMap(accountId -> Mono.fromFuture(manager.getAccountWithId(accountId)))
                        .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Username not found")))
                        // update the session
                        .flatMap(accountWithId -> {
                            if (!ApolloApiHelpers.matchesPassword(params.password, accountWithId.account.pwdHash)) {
                                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Password does not match");
                            }
                            return this.loginWithAccount(session, params.scope, accountWithId);
                        });
         } else if ("client_credentials".equals(params.grant_type)) return Mono.just(new GetToken(session.getId(), params.scope));
         /*  
         else if ("authorization_code".equals(params.grant_type)) {
             if (params.code == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
             return Mono.fromFuture(manager.getAccountIdFromAuthCode(params.code))
                        .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                        // get the account
                        .flatMap(accountId -> Mono.fromFuture(manager.getAccountWithId(accountId)))
                        .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                        .flatMap(accountWithId ->
                                Mono.fromFuture(manager.postRemoveAuthCode(params.code))
                                    .flatMap(res -> this.loginWithAccount(session, params.scope, accountWithId)));
         } */
         else throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        
     }
 
     @PostMapping(value = "/oauth/token", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
     public Mono<GetToken> postOauthToken(WebSession session, ServerWebExchange exchange) {
         return exchange.getFormData()
                        .flatMap(formParams -> {
                            PostToken params = ApolloApiFormParser.parseParams(formParams, new PostToken());
                            return this.postOauthToken(session, params);
                        });
     }
    
     /**
 * Handles requests to revoke OAuth tokens, supporting both JSON and form URL-encoded data formats.
 * This functionality is critical for maintaining the security of the application by allowing tokens
 * to be invalidated when they are no longer needed - i.e. this is called when the user logs out.
 * The endpoint accepts a token identifier in the request and proceeds to revoke the specified token.
 * 
 * - For JSON requests, the token to be revoked is specified in the body of the request.
 * - For form URL-encoded requests, the token information is extracted from the form data.
 * 
 * In both cases, the token is revoked by removing any associated authorization codes or access tokens
 * from the system, effectively disabling any further use of the token for authentication or authorization purposes.
 */

 // TODO: See if this still works
 @PostMapping(value = "/oauth/revoke", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Mono<Object> postRevokeOauthToken(@RequestBody(required = true) PostRevokeToken params) {
        return Mono.fromFuture(manager.postRemoveAuthCode(params.token)).map(res -> new HashMap<String, Object>());
    }

    @PostMapping(value = "/oauth/revoke", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public Mono<Object> postRevokeOauthToken(ServerWebExchange exchange) {
        return exchange.getFormData()
                       .flatMap(formParams -> {
                           PostRevokeToken params = ApolloApiFormParser.parseParams(formParams, new PostRevokeToken());
                           return this.postRevokeOauthToken(params);
                       });
    }

    
}
