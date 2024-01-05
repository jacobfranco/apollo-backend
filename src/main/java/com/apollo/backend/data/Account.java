package com.apollo.backend.data;

public class Account {

    public String username;
    public String email;
    public String displayName;
    public String bio;
    public String pwdHash;
    public String locale;
    public String profilePic;
    public long joinedAtMillis;
    public String uuid;
    public String publicKey;

    public Account() { }

    public Account(PostAccount postAccount, String pwdHash, String uuid, String publicKey, long joinedAtMillis) {
        this.username = postAccount.username;
        this.email = postAccount.email;
        this.pwdHash = pwdHash;
        this.locale = postAccount.locale;
        this.joinedAtMillis = joinedAtMillis;
        this.uuid = uuid;
        this.publicKey = publicKey;
    }


    }

