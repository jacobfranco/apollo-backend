package com.apollo.backend.data;

import com.rpl.rama.RamaSerializable;

public class Account implements RamaSerializable {
  public String email;
  public String username;
  public String displayName;
  public String bio;
  public String location;
  public String profilePic;
  public long joinedAtMillis;

  public Account(String email, String username, String displayName, String bio, String location, String profilePic, long joinedAtMillis) {
    this.email = email;
    this.username = username;
    this.displayName = displayName;
    this.bio = bio;
    this.location = location;
    this.profilePic = profilePic;
    this.joinedAtMillis = joinedAtMillis;
  }
}