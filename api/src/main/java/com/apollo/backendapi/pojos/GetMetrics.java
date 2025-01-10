package com.apollo.backendapi.pojos;

import com.apollo.backendapi.ApolloApiMetrics;

import java.time.LocalDateTime;
import java.util.Map;

public class GetMetrics {
    public Map<LocalDateTime, ApolloApiMetrics.Metrics> hourly;

    public GetMetrics(Map<LocalDateTime, ApolloApiMetrics.Metrics> hourly) {
        this.hourly = hourly;
    }
}