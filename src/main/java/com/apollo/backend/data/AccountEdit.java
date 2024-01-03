package com.apollo.backend.data;

import com.rpl.rama.RamaSerializable;

public class AccountEdit implements RamaSerializable {
  public String userId;
  public String field;
  public Object value;

  public AccountEdit(String userId, String field, Object value) {
    this.userId = userId;
    this.field = field;
    this.value = value;
  }
}
