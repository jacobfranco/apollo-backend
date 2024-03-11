package com.apollo.backendapi.pojos;

import com.apollo.backendapi.ApolloApiConfig;

public class GetApplication {
    public String name = "Apollo";
    public String redirect_uri;
    public String client_id = ApolloApiConfig.OAUTH_CLIENT_ID;
    public String client_secret;
}
