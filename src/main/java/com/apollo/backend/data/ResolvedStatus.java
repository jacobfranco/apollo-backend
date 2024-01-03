package com.apollo.backend.data;

import com.rpl.rama.RamaSerializable;

public class ResolvedStatus implements RamaSerializable {
  public String userId;
  public String content;
  public String displayName;
  public String profilePic;

  public ResolvedStatus(String userId, String content, String displayName, String profilePic) {
    this.userId = userId;
    this.content = content;
    this.displayName = displayName;
    this.profilePic = profilePic;
  }
}
