package com.apollo.backend.data;

public class GetAccount {
  public String email;
  public String username;
  public String displayName;
  public String bio;
  public String locale;
  public String profilePic;

  public GetAccount() { }

  public GetAccount(String email, String username, String displayName, String bio, String locale, String profilePic) {
    this.email = email;
    this.username = username;
    this.displayName = displayName;
    this.bio = bio;
    this.locale = locale;
    this.profilePic = profilePic;
  }
}