package com.apollo.backendapi;

import java.util.concurrent.*;

import org.springframework.context.annotation.*;

import com.apollo.backend.data.*;

@Configuration
public class ApolloApiStreamingConfig {
 
 // caches of the latest query results of each global timeline
   public static final ConcurrentHashMap<LocalTimeline, ConcurrentSkipListMap<Long, StatusQueryResult>> LOCAL_TIMELINE_TO_INDEX_TO_STATUS = new ConcurrentHashMap() {{
    put(LocalTimeline.Public, new ConcurrentSkipListMap<>());
}};
public static final ConcurrentHashMap<LocalTimeline, ConcurrentHashMap<StatusPointer, Long>> LOCAL_TIMELINE_TO_STATUS_POINTER_TO_INDEX = new ConcurrentHashMap() {{
    put(LocalTimeline.Public, new ConcurrentHashMap<>());
}};
}