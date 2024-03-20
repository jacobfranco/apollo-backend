package com.apollo.backendapi;

public class ApolloApiConfig {
    
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