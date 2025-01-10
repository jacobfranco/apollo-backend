package com.apollo.backendapi;

import com.rpl.rama.ProxyState;
import org.springframework.context.annotation.*;
import org.springframework.scheduling.annotation.*;

import java.util.*;

import static com.apollo.backendapi.ApolloApiStreamingConfig.*;

@Configuration
@EnableScheduling
public class ApolloApiSchedulingConfig {
    @Scheduled(fixedDelay = 5000)
    public static void refreshHomeTimelineProxies() {
        if (ApolloApiController.manager == null)
            return; // exit early if the manager hasn't been initialized yet
        List<ApolloApiManager.HomeTimelineProxyState> proxies = new ArrayList();
        for (StreamState ss : SESSION_ID_TO_STATE.values()) {
            for (ProxyState<SortedMap> proxy : ss.proxies) {
                if (proxy instanceof ApolloApiManager.HomeTimelineProxyState)
                    proxies.add((ApolloApiManager.HomeTimelineProxyState) proxy);
            }
        }
        if (!proxies.isEmpty())
            ApolloApiController.manager.refreshHomeTimelineProxies(proxies);
    }

}