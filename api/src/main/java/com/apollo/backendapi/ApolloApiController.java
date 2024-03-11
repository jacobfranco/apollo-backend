package com.apollo.backendapi;

import com.apollo.backendapi.pojos.*;

import java.security.NoSuchAlgorithmException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.*;
import org.springframework.http.*;
import reactor.core.publisher.Mono;

// TODO: Implement

@RestController
@CrossOrigin(exposedHeaders = {"Link"})
public class ApolloApiController {

 /**
 * Handle creation of a new OAuth application for the Apollo API.
 * Generates a random client_secret and returns a GetApplication object with the provided redirect_uri.
 * Accepts JSON or URL-encoded request bodies.
 */
    @PostMapping(value = "/api/v1/apps", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Mono<GetApplication> postApplication(@RequestBody(required = true) PostApplication params) throws NoSuchAlgorithmException {
        // currently, the application isn't saved anywhere
        GetApplication app = new GetApplication();
        app.redirect_uri = params.redirect_uris;
        app.client_secret = "secret_" + ApolloApiHelpers.randomString(16);
        return Mono.just(app);
    }

    @PostMapping(value = "/api/v1/apps", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
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
    
}