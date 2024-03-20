package com.apollo.backend;

public class ApolloHelpers {

  public static Long parseAccountId(String id) {
    if (id == null) return null;
    String[] parts = id.split("-");
    if ("a".equals(parts[parts.length-1]) && parts.length == 2) return Long.parseLong(parts[0]);
    else throw new RuntimeException("Not an account id: " + id);
  }
  
}