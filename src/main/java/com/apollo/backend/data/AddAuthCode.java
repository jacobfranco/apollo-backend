package com.apollo.backend.data;

import java.io.Serializable;

public class AddAuthCode implements Serializable, Comparable<AddAuthCode> {
    private String code;
    private long accountId;

    public AddAuthCode() {
    }

    public AddAuthCode(String code, long accountId) {
        this.code = code;
        this.accountId = accountId;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public long getAccountId() {
        return accountId;
    }

    public void setAccountId(long accountId) {
        this.accountId = accountId;
    }

    @Override
    public int compareTo(AddAuthCode other) {
        // Implement comparison logic if needed, possibly based on code or accountId
        return 0; // Placeholder implementation
    }

    // Additional methods like equals, hashCode, and toString can be added as needed.
}
