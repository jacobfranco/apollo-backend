package com.apollo.backend.pojos;

public class GetAccount {
  private String email;
  private String username;
  private String displayName;
  private String bio;
  private String locale;
  private String profilePic;

  public GetAccount() { }

  public GetAccount(String email, String username, String displayName, String bio, String locale, String profilePic) {
    this.email = email;
    this.username = username;
    this.displayName = displayName;
    this.bio = bio;
    this.locale = locale;
    this.profilePic = profilePic;
  }

  public String getEmail() {
    return email;
  }

  public String getUsername() {
    return username;
  }

  public String getDisplayName() {
    return displayName;
  }

  public String getBio() {
    return bio;
  }

  public String getLocale() {
    return locale;
  }

  public String getProfilePic() {
    return profilePic;
  }
}