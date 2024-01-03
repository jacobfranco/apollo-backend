package com.apollo.backend.api;

import com.apollo.backend.modules.*;
import com.rpl.rama.test.*;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.*;
import java.security.*;
import java.util.*;

@SpringBootApplication
public class ApolloApiApplication {
    public static void main(String[] args) throws NoSuchAlgorithmException, IOException, NoSuchProviderException {
        // Initialize IPC and Spring Application
        initIPC();
        SpringApplication.run(ApolloApiApplication.class, args);
    }

    public static InProcessCluster initIPC() throws NoSuchAlgorithmException, IOException, NoSuchProviderException {
        InProcessCluster ipc = InProcessCluster.create(new ArrayList<>()); // Assuming serialization class is added if needed

        // Launching various modules
        launchModules(ipc);

        ApolloApiController.manager = new ApolloApiManager(ipc);

        return ipc;
    }

    private static void launchModules(InProcessCluster ipc) throws IOException {
        // Example of launching a module
        Core coreModule = new Core();
        ipc.launchModule(coreModule, new LaunchConfig(2, 1));

    }


}
