package com.apollo.backend.data;

import com.apollo.backend.pojos.PostAccount;

public class Account {

    // private String username;
    // private String email;
   //  private String displayName;
    private String bio;
    // private String pwdHash;
    // private String locale;
    private String profilePic;
    private long joinedAtMillis;
   //  private String uuid;
    // private String publicKey;

    public Account() { }

    public Account(PostAccount postAccount, String pwdHash, String uuid, String publicKey, long joinedAtMillis) {
        this.username = postAccount.getUsername();
        this.email = postAccount.getEmail();
        this.pwdHash = pwdHash;
        this.locale = postAccount.getLocale();
        this.joinedAtMillis = joinedAtMillis;
        this.uuid = uuid;
        this.publicKey = publicKey;
    }

    // TODO: User uploads profile details - display name, bio, and profile picture

    public String getUsername() {
        return username;
    }

    public String getEmail() {
        return email;
    }

    public String getPwdHash() {
        return pwdHash;
    }

    public String getLocale() {
        return locale;
    }

    public long getJoinedAtMillis() {
        return joinedAtMillis;
    }

    public String getUuid() {
        return uuid;
    }

    public String getPublicKey() {
        return publicKey;
    }

    }

