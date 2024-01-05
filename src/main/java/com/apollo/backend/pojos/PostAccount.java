package com.apollo.backend.pojos;

public class PostAccount {
    private String username;
    private String email;
    private String password;
    private Boolean agreement;
    private String locale;

    public PostAccount() { }

    public PostAccount(String username, String email, String password, Boolean agreement, String locale) {
        this.username = username;
        this.email = email;
        this.password = password;
        this.agreement = agreement;
        this.locale = locale;
    }

    public String getUsername() {
        return username;
    }

    public String getEmail() {
        return email;
    }

    public String getPassword() {
        return password;
    }

    public Boolean getAgreement() {
        return agreement;
    }

    public String getLocale() {
        return locale;
    }
}
