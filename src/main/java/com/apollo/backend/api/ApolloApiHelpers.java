package com.apollo.backend.api;

import java.util.*;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.*;

public class ApolloApiHelpers {

    private static final DelegatingPasswordEncoder PASSWORD_ENCODER;

    static {
        HashMap<String, PasswordEncoder> encoders = new HashMap<>();
        encoders.put("bcrypt", new BCryptPasswordEncoder());
        PASSWORD_ENCODER = new DelegatingPasswordEncoder("bcrypt", encoders);
    }

    public static String encodePassword(String password) {
        return PASSWORD_ENCODER.encode(password);
    }
    
}
