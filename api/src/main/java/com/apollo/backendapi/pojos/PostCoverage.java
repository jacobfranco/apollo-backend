package com.apollo.backendapi.pojos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class PostCoverage {
    public PostCoverageData data;

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PostCoverageData {
        public PostCoverageType live;
        public PostCoverageType realtime;
        public PostCoverageType postgame;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PostCoverageType {
        public PostCoverageStatus api;
        public PostCoverageStatus cv;
        public PostCoverageStatus server;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PostCoverageStatus {
        public String expectation;
        public String fact;
    }
}