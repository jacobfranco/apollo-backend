package com.apollo.backendapi;

import com.apollo.backend.*;
import com.apollo.backend.data.*;
import com.apollo.backend.modules.*;
import com.apollo.backend.serialization.ApolloSerialization;

import com.rpl.rama.*;
import com.rpl.rama.test.*;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import software.amazon.awssdk.core.exception.SdkClientException;

import java.io.*;
import java.security.*;
import java.util.*;

import javax.annotation.PostConstruct;

import org.springframework.context.ApplicationContext;

@SpringBootApplication
public class ApolloApiApplication {
        // Main method to start the application
        public static void main(String[] args) throws NoSuchAlgorithmException, IOException, NoSuchProviderException {
                // Check if more than one argument is provided to configure API URLs
                if (args.length > 1) {
                        ApolloConfig.API_URL = args[1];
                        ApolloConfig.API_WEB_SOCKET_URL = args[2];
                        ApolloConfig.API_DOMAIN = args[3];
                        ApolloConfig.FRONTEND_URL = args[4];
                }

                // Initialize Amazon S3 client
                try {
                        ApolloApiHelpers.initS3Client();
                } catch (SdkClientException e) {
                        // Handle exceptions by printing stack trace and setting S3 options to null
                        e.printStackTrace();
                        ApolloApiConfig.S3_OPTIONS = null;
                }

                // Initialize the cluster manager with configuration if arguments are provided
                if (args.length > 0) {
                        ApolloApiController.manager = new ApolloApiManager(
                                        RamaClusterManager.openInternal(new HashMap<String, Object>() {
                                                {
                                                        // Configuration map for cluster manager
                                                        put("conductor.host", args[0]);
                                                        put("custom.serializations",
                                                                        Arrays.asList("com.apollo.backend.serialization.ApolloSerialization"));
                                                }
                                        }));
                } else {
                        // Initialize In-Process Cluster if no arguments are provided
                        initIPC();
                }

                // Start the Spring application
                SpringApplication.run(ApolloApiApplication.class, args);
        }

        // For Testing
        public static RamaClusterManager initRealCluster()
                        throws IOException, NoSuchAlgorithmException, NoSuchProviderException {
                RamaClusterManager cluster = RamaClusterManager.openInternal();
                ApolloApiController.manager = new ApolloApiManager(cluster);
                Depot accountDepot = cluster.clusterDepot(Core.class.getName(), "*accountDepot");
                ApolloWebHelpers.SigningKeyPair aliceKeys = ApolloWebHelpers.generateKeys();
                accountDepot.append(new Account("alice", "alice@foo.com", ApolloApiHelpers.encodePassword("alice"),
                                "en-US",
                                UUID.randomUUID().toString(), aliceKeys.publicKey, System.currentTimeMillis(), false,
                                false));
                ApolloWebHelpers.SigningKeyPair bobKeys = ApolloWebHelpers.generateKeys();
                accountDepot.append(new Account("bob", "bob@foo.com", ApolloApiHelpers.encodePassword("bob"), "en-US",
                                UUID.randomUUID().toString(), bobKeys.publicKey, System.currentTimeMillis(), false,
                                false));
                return cluster;
        }

        public static InProcessCluster initIPC() throws NoSuchAlgorithmException, IOException, NoSuchProviderException {
                @SuppressWarnings("rawtypes") // Suppress warnings for raw types in the list of serializers
                List<Class> sers = new ArrayList<>();
                sers.add(ApolloSerialization.class);
                InProcessCluster ipc = InProcessCluster.create(sers);

                // Instantiate and launch modules
                Relationships relationshipsModule = new Relationships();
                String relationshipsModuleName = Relationships.class.getName();
                ipc.launchModule(relationshipsModule, new LaunchConfig(2, 1));

                Core coreModule = new Core();
                String coreModuleName = Core.class.getName();
                ipc.launchModule(coreModule, new LaunchConfig(2, 1));

                TrendsAndHashtags hashtagsModule = new TrendsAndHashtags();
                ipc.launchModule(hashtagsModule, new LaunchConfig(2, 1));

                GlobalTimelines globalTimelinesModule = new GlobalTimelines();
                ipc.launchModule(globalTimelinesModule, new LaunchConfig(2, 1));

                Notifications notificationsModule = new Notifications();
                ipc.launchModule(notificationsModule, new LaunchConfig(2, 1));

                Search searchModule = new Search();
                ipc.launchModule(searchModule, new LaunchConfig(2, 1));

                ESports eSportsModule = new ESports();
                ipc.launchModule(eSportsModule, new LaunchConfig(2, 1));

                ApolloApiController.manager = new ApolloApiManager(ipc);

                int weekMillis = 1000 * 60 * 60 * 24 * 7;
                long ts = System.currentTimeMillis() - weekMillis;

                Depot accountDepot = ipc.clusterDepot(coreModuleName, "*accountDepot");
                ApolloWebHelpers.SigningKeyPair aliceKeys = ApolloWebHelpers.generateKeys();
                accountDepot.append(
                                new Account("alice", "alice@foo.com", ApolloApiHelpers.encodePassword("alice"), "en-US",
                                                UUID.randomUUID().toString(), aliceKeys.publicKey, ts += 1, false,
                                                false));

                ApolloWebHelpers.SigningKeyPair bobKeys = ApolloWebHelpers.generateKeys();
                accountDepot.append(
                                new Account("bob", "bob@foo.com", ApolloApiHelpers.encodePassword("bob"), "en-US",
                                                UUID.randomUUID().toString(), bobKeys.publicKey, ts += 1, false,
                                                false));
                ApolloWebHelpers.SigningKeyPair jacobKeys = ApolloWebHelpers.generateKeys();
                accountDepot.append(new Account("jacob", "jacob@foo.com", ApolloApiHelpers.encodePassword("jacob"),
                                "en-US", UUID.randomUUID().toString(), jacobKeys.publicKey, ts += 1, true, true));
                ApolloWebHelpers.SigningKeyPair jordanKeys = ApolloWebHelpers.generateKeys();
                accountDepot.append(
                                new Account("jordan", "jordan@apollo.now", ApolloApiHelpers.encodePassword("jordan"),
                                                "en-US",
                                                UUID.randomUUID().toString(), jordanKeys.publicKey, ts += 1, true,
                                                true));
                ApolloWebHelpers.SigningKeyPair blakeKeys = ApolloWebHelpers.generateKeys();
                accountDepot.append(
                                new Account("blake", "blake@apollo.now", ApolloApiHelpers.encodePassword("blake"),
                                                "en-US",
                                                UUID.randomUUID().toString(), blakeKeys.publicKey, ts += 1, true,
                                                true));

                List<Long> fooIds = new ArrayList<>();
                PState nameToUser = ipc.clusterPState(coreModuleName, "$$nameToUser");
                for (int i = 0; i < 50; i++) {
                        ApolloWebHelpers.SigningKeyPair keys = ApolloWebHelpers.generateKeys();
                        accountDepot
                                        .append(new Account("foo" + i, "foo" + i + "@foo.com",
                                                        ApolloApiHelpers.encodePassword("jacob"),
                                                        "en-US", UUID.randomUUID().toString(), keys.publicKey, ts += 1,
                                                        false, false)
                                                        .setDiscoverable(true)
                                                        .setDisplayName("Foo " + i));
                        long fooId = nameToUser.selectOne(Path.key("foo" + i, "accountId"));
                        fooIds.add(fooId);
                }

                PState accountIdToStatuses = ipc.clusterPState(coreModuleName, "$$accountIdToStatuses");
                long aliceId = nameToUser.selectOne(Path.key("alice", "accountId"));
                long bobId = nameToUser.selectOne(Path.key("bob", "accountId"));

                Depot followAndBlockAccountDepot = ipc.clusterDepot(relationshipsModuleName,
                                "*followAndBlockAccountDepot");
                followAndBlockAccountDepot.append(new FollowAccount(bobId, aliceId, ts += 1));

                for (long fooId : fooIds) {
                        followAndBlockAccountDepot.append(new FollowAccount(bobId, fooId, ts += 1));
                        followAndBlockAccountDepot.append(new FollowAccount(fooId, bobId, ts += 1));
                        followAndBlockAccountDepot.append(new FollowAccount(fooId, aliceId, ts += 1));
                }

                Depot statusDepot = ipc.clusterDepot(Core.class.getName(), "*statusDepot");
                for (int i = 0; i < 50; i++) {
                        ts += weekMillis / 50;
                        statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(aliceId,
                                        StatusContent.normal(new NormalStatusContent(i + " Hello, world!",
                                                        StatusVisibility.Public)),
                                        ts)));
                        statusDepot
                                        .append(new AddStatus(UUID.randomUUID().toString(),
                                                        new Status(bobId,
                                                                        StatusContent.normal(new NormalStatusContent(
                                                                                        i + " #LeagueOfLegends https://github.com",
                                                                                        StatusVisibility.Public)),
                                                                        ts)));
                        statusDepot.append(new AddStatus(UUID.randomUUID().toString(),
                                        new Status(aliceId, StatusContent.normal(
                                                        new NormalStatusContent(i + " @bob this is a direct message",
                                                                        StatusVisibility.Direct)),
                                                        ts)));
                        long aliceDirect = accountIdToStatuses.selectOne(Path.key(aliceId).first().first());
                        statusDepot.append(new AddStatus(UUID.randomUUID().toString(),
                                        new Status(bobId,
                                                        StatusContent.reply(new ReplyStatusContent(
                                                                        i + " @alice this is also a direct message",
                                                                        StatusVisibility.Direct,
                                                                        new StatusPointer(aliceId, aliceDirect))),
                                                        ts)));
                        statusDepot.append(new AddStatus(UUID.randomUUID().toString(),
                                        new Status(aliceId, StatusContent.normal(
                                                        new NormalStatusContent(i + " @bob this is a public message",
                                                                        StatusVisibility.Public)),
                                                        ts)));
                        long alicePublic = accountIdToStatuses.selectOne(Path.key(aliceId).first().first());
                        statusDepot.append(new AddStatus(UUID.randomUUID().toString(),
                                        new Status(bobId,
                                                        StatusContent.reply(new ReplyStatusContent(
                                                                        i + " @alice this is also a public message",
                                                                        StatusVisibility.Public,
                                                                        new StatusPointer(aliceId, alicePublic))),
                                                        ts)));
                }

                // Like and boost statuses as part of the simulation
                Depot likeStatusDepot = ipc.clusterDepot(Core.class.getName(), "*likeStatusDepot");
                StatusQueryResults aliceTimeline = null;
                try {
                        aliceTimeline = ApolloApiController.manager
                                        .getAccountTimeline(null, aliceId, null, null, false, true)
                                        .get();
                } catch (Exception e) {
                        throw new RuntimeException(e);
                }
                StatusResultWithId aliceStatus = aliceTimeline.results.get(0);
                StatusPointer aliceStatusPointer = new StatusPointer(aliceId, aliceStatus.statusId);
                for (long fooId : fooIds) {
                        likeStatusDepot.append(new LikeStatus(fooId, aliceStatusPointer, System.currentTimeMillis()));
                        statusDepot.append(new BoostStatus(UUID.randomUUID().toString(), fooId, aliceStatusPointer,
                                        System.currentTimeMillis()));
                }

                return ipc;
        }

        @PostConstruct
        public void initializeESportsData() {
                System.out.println("Application context loaded. Fetching eSports data...");
                if (ApolloApiController.manager != null) {
                        ApolloApiController.manager.fetchAllActiveLolTeams();
                        ApolloApiController.manager.fetchAllActiveLolPlayers();
                        ApolloApiController.manager.fetchAllLolTournaments();
                        ApolloApiController.manager.fetchAllLolSubstages();
                        ApolloApiController.manager.fetchAllLolCasters();
                        ApolloApiController.manager.fetchAllLolAssets();
                        ApolloApiController.manager.fetchAllLolSeries(ApolloApiConfig.LOL_START_TEST,
                                        ApolloApiConfig.LOL_END_TEST);
                        // ApolloApiController.manager.fetchAllLolSeries(ApolloApiConfig.LOL_SEASON_START,
                        // ApolloApiConfig.LOL_SEASON_END);
                        // Start WebSocket connection after data fetching
                        try {
                                ApolloApiController.manager.startWebSocketConnection();
                                System.out.println("WebSocket connection started successfully.");
                        } catch (Exception e) {
                                System.err.println("Error starting WebSocket connection: " + e.getMessage());
                        }
                } else {
                        System.err.println("ApolloApiController.manager is null. Unable to fetch eSports data.");
                }
        }

        @PostConstruct
        public void initializeSpaces() {
                System.out.println("Initializing spaces");
                if (ApolloApiController.manager != null) {
                        ApolloApiController.manager.initializeSpaces(ApolloApiHelpers.SPACES);
                } else {
                        System.err.println("ApolloApiController.manager is null. Unable to fetch eSports data.");
                }
        }

}