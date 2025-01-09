package com.apollo.backendapi.pojos;

import com.apollo.backend.data.Application;

public class GetApplication {
    public String name = "Apollo";
    public String redirect_uri;
    public String client_id;
    public String client_secret;

    public GetApplication() {
    } // Default constructor

    public GetApplication(Application app) {
        this.name = app.getName();
        this.redirect_uri = app.getRedirect_uri();
        this.client_id = app.getClient_id();
        this.client_secret = app.getClient_secret();
    }

}
