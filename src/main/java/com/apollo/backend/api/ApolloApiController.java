package com.apollo.backend.api;

import java.util.*;

import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.*;
import org.springframework.http.*;

import com.apollo.backend.data.*;
import com.apollo.backend.pojos.GetErrorDetails;
import com.apollo.backend.pojos.GetToken;
import com.apollo.backend.pojos.PostAccount;

import reactor.core.publisher.Mono;

@RestController
@CrossOrigin(exposedHeaders = {"Link"})
public class ApolloApiController {

    public static ApolloApiManager manager;

    @PostMapping("/api/v1/accounts")
    public Mono<?> postAccount(WebSession session, ServerHttpResponse response, @RequestBody(required = true) PostAccount params) {
        if (!params.getUsername().matches("[a-zA-Z0-9]*")) {
            response.setStatusCode(HttpStatus.UNPROCESSABLE_ENTITY);
            return Mono.just(new GetErrorDetails("Username must contain only a-z or numbers", new HashMap<String, GetErrorDetails.Error>(){{
                put("username", new GetErrorDetails.Error("ERR_INVALID", "Username must contain only a-z or numbers"));
            }}));
        } else if(params.getUsername().length() > ApolloApiConfig.MAX_USERNAME_LENGTH) {
          response.setStatusCode(HttpStatus.UNPROCESSABLE_ENTITY);
          return Mono.just(new GetErrorDetails("Username too long", new HashMap<String, GetErrorDetails.Error>(){{
              put("agreement", new GetErrorDetails.Error("ERR_INVALID", "Username cannot be greater than " + ApolloApiConfig.MAX_USERNAME_LENGTH + " characters"));
          }}));
        } else if (!params.getAgreement()) {
            response.setStatusCode(HttpStatus.UNPROCESSABLE_ENTITY);
            return Mono.just(new GetErrorDetails("The agreement has not been accepted", new HashMap<String, GetErrorDetails.Error>(){{
                put("agreement", new GetErrorDetails.Error("ERR_ACCEPTED", "The agreement has not been accepted"));
            }}));
        }
        return Mono.fromFuture(manager.postAccount(params))
                   .flatMap(success -> {
                       if (success) {
                           return Mono.fromFuture(manager.getAccountId(params.getUsername()))
                                      .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                                      // get the account
                                      .flatMap(accountId -> Mono.fromFuture(manager.getAccountWithId(accountId)))
                                      .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                                      // update the session
                                      .flatMap(accountWithId -> this.loginWithAccount(session, "read write follow push", accountWithId));
                       }
                       else {
                           response.setStatusCode(HttpStatus.UNPROCESSABLE_ENTITY);
                           return Mono.just(new GetErrorDetails("Validation failed", new HashMap<String, GetErrorDetails.Error>(){{
                               put("username", new GetErrorDetails.Error("ERR_TAKEN", "Username already in use"));
                           }}));
                       }
                   });
    }

    private Mono<GetToken> loginWithAccount(WebSession session, String scope, AccountWithId accountWithId) {
        // update session
        session.getAttributes().put("accountId", accountWithId.getAccountId());
        session.getAttributes().put("accountName", accountWithId.getAccount().getUsername());
        // store the session id in the backend and return token
        return Mono.fromFuture(manager.postAuthCode(accountWithId.getAccountId(), session.getId())).map(res -> new GetToken(session.getId(), scope));
    }
    
}
