package com.apollo.backendapi;

import com.apollo.backend.data.*;

import java.io.*;
import java.net.*;
import java.security.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import org.bouncycastle.util.encoders.Hex;
import org.springframework.security.crypto.bcrypt.*;
import org.springframework.security.crypto.password.*;
import org.springframework.web.server.*;
import org.springframework.http.*;

import software.amazon.awssdk.auth.credentials.EnvironmentVariableCredentialsProvider;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.*;


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

    public static boolean matchesPassword(String password, String passwordHash) {
        return PASSWORD_ENCODER.matches(password, passwordHash);
    }

     public static String randomString(int size) throws NoSuchAlgorithmException {
        byte[] bytes = new byte[size];
        SecureRandom.getInstanceStrong().nextBytes(bytes);
        return Hex.toHexString(bytes);
    }

     private static S3AsyncClient S3_CLIENT = null;
    public static void initS3Client() {
        S3_CLIENT = S3AsyncClient.builder().credentialsProvider(EnvironmentVariableCredentialsProvider.create()).build();
    }

    public static CompletableFuture<PutObjectResponse> uploadToS3(String bucketName, String key, File file) {
        PutObjectRequest objectRequest = PutObjectRequest.builder().bucket(bucketName).key(key).build();
        return S3_CLIENT.putObject(objectRequest, AsyncRequestBody.fromFile(file));
    }

     public static boolean isValidURL(String url) {
        try {
            new URL(url).toURI();
            return true;
        } catch (MalformedURLException | URISyntaxException e) {
            return false;
        }
    }

    public static String createFilterContext(FilterContext context) {
        switch (context) {
            case Home: return "home";
            case Notifications: return "notifications";
            case Public: return "public";
            case Thread: return "thread";
            case Account: return "account";
        }
        throw new RuntimeException("Invalid filter context");
    }

    public static String createFilterAction(FilterAction action) {
        switch (action) {
            case Warn: return "warn";
            case Hide: return "hide";
        }
        throw new RuntimeException("Invalid filter action");
    }

    public static StatusVisibility createStatusVisibility(String visibilityStr) {
        if ("public".equals(visibilityStr)) return StatusVisibility.Public;
        else if ("unlisted".equals(visibilityStr)) return StatusVisibility.Unlisted;
        else if ("private".equals(visibilityStr)) return StatusVisibility.Private;
        else if ("direct".equals(visibilityStr)) return StatusVisibility.Direct;
        else throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
    }

    public static String createStatusVisibility(StatusVisibility visibility) {
        switch (visibility) {
            case Public: return "public";
            case Unlisted: return "unlisted";
            case Private: return "private";
            case Direct: return "direct";
        }
        throw new RuntimeException("Invalid visibility");
    }

}