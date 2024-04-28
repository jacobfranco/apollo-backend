package com.apollo.backendapi;

public class ApolloApiConfig {

    //TODO: OAUth Client ID
    public static final String OAUTH_CLIENT_ID = "";
    public static final String STATIC_FILE_URL_PATH_NAME = "uploads";
    public static final int MAX_STATUS_LENGTH = 500;
    public static final int MAX_POLL_CHOICE_LENGTH = 30;
    
    public static final int MAX_USERNAME_LENGTH = 30;
    public static class S3Options {
        public String bucketName;
        public String url;
    }
    public static S3Options S3_OPTIONS = null;
    static {
        S3_OPTIONS = new S3Options();
        S3_OPTIONS.bucketName = "yourbucket"; // TODO: Fill in with details
        S3_OPTIONS.url = "https://yourbucket.s3.amazonaws.com";
    }
}