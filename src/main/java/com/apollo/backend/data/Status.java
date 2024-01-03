package com.apollo.backend.data;

import com.rpl.rama.RamaSerializable;

public class Status implements RamaSerializable {
  public String userId;
  public String toUserId;
  public String content;

  public Status(String userId, String toUserId, String content) {
    this.userId = userId;
    this.toUserId = toUserId;
    this.content = content;
  }
} 
