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

@SpringBootApplication
public class ApolloApiApplication {
    public static void main(String[] args) throws NoSuchAlgorithmException, IOException, NoSuchProviderException {
        if(args.length > 1) {
          ApolloConfig.API_URL = args[1];
          ApolloConfig.API_WEB_SOCKET_URL = args[2];
          ApolloConfig.API_DOMAIN = args[3];
          ApolloConfig.FRONTEND_URL = args[4];
        }

        // init s3
        try {
            ApolloApiHelpers.initS3Client();
        } catch (SdkClientException e) {
            e.printStackTrace();
            ApolloApiConfig.S3_OPTIONS = null;
        }

        // init cluster manager
        if(args.length > 0) {
          ApolloApiController.manager = new ApolloApiManager(RamaClusterManager.openInternal(new HashMap() {{
            put("conductor.host", args[0]);
            put("custom.serializations", Arrays.asList("com.apollo.backend.serialization.ApolloSerialization"));
          }}));
        } else initIPC();

        // init spring
        SpringApplication.run(ApolloApiApplication.class, args);
    }

    // For Testing
    public static RamaClusterManager initRealCluster() throws IOException, NoSuchAlgorithmException, NoSuchProviderException {
        RamaClusterManager cluster = RamaClusterManager.openInternal();
        ApolloApiController.manager = new ApolloApiManager(cluster);
        Depot accountDepot = cluster.clusterDepot(Core.class.getName(), "*accountDepot");
        ApolloWebHelpers.SigningKeyPair aliceKeys = ApolloWebHelpers.generateKeys();
        accountDepot.append(new Account("alice", "alice@foo.com", ApolloApiHelpers.encodePassword("alice"), "en-US", UUID.randomUUID().toString(), aliceKeys.publicKey, AccountContent.local(new LocalAccount(aliceKeys.privateKey)), System.currentTimeMillis()));
        ApolloWebHelpers.SigningKeyPair bobKeys = ApolloWebHelpers.generateKeys();
        accountDepot.append(new Account("bob", "bob@foo.com", ApolloApiHelpers.encodePassword("bob"), "en-US", UUID.randomUUID().toString(), bobKeys.publicKey, AccountContent.local(new LocalAccount(bobKeys.privateKey)), System.currentTimeMillis()));
        return cluster;
    }

    public static InProcessCluster initIPC() throws NoSuchAlgorithmException, IOException, NoSuchProviderException {
        List<Class> sers = new ArrayList<>();
        sers.add(ApolloSerialization.class);
        InProcessCluster ipc = InProcessCluster.create(sers);

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

        ApolloApiController.manager = new ApolloApiManager(ipc);

        int weekMillis = 1000 * 60 * 60 * 24 * 7;
        long ts = System.currentTimeMillis() - weekMillis;

        Depot accountDepot = ipc.clusterDepot(coreModuleName, "*accountDepot");
        ApolloWebHelpers.SigningKeyPair aliceKeys = ApolloWebHelpers.generateKeys();
        accountDepot.append(new Account("alice", "alice@foo.com", ApolloApiHelpers.encodePassword("alice"), "en-US", UUID.randomUUID().toString(), aliceKeys.publicKey, AccountContent.local(new LocalAccount(aliceKeys.privateKey)), ts+=1));
        ApolloWebHelpers.SigningKeyPair bobKeys = ApolloWebHelpers.generateKeys();
        accountDepot.append(new Account("bob", "bob@foo.com", ApolloApiHelpers.encodePassword("bob"), "en-US", UUID.randomUUID().toString(), bobKeys.publicKey, AccountContent.local(new LocalAccount(bobKeys.privateKey)), ts+=1));
        ApolloWebHelpers.SigningKeyPair charlieKeys = ApolloWebHelpers.generateKeys();
        accountDepot.append(new Account("charlie", "charlie@foo.com", ApolloApiHelpers.encodePassword("charlie"), "en-US", UUID.randomUUID().toString(), charlieKeys.publicKey, AccountContent.local(new LocalAccount(charlieKeys.privateKey)), ts+=1));

        List<Long> fooIds = new ArrayList<>();
        PState nameToUser = ipc.clusterPState(coreModuleName, "$$nameToUser");
        for (int i = 0; i < 50; i++) {
            ApolloWebHelpers.SigningKeyPair keys = ApolloWebHelpers.generateKeys();
            accountDepot.append(new Account("foo" + i, "foo" + i + "@foo.com", ApolloApiHelpers.encodePassword("charlie"), "en-US", UUID.randomUUID().toString(), keys.publicKey, AccountContent.local(new LocalAccount(keys.privateKey)), ts+=1).setDiscoverable(true).setDisplayName("Foo " + i));
            long fooId = nameToUser.selectOne(Path.key("foo" + i, "accountId"));
            fooIds.add(fooId);
        }

        PState accountIdToStatuses = ipc.clusterPState(coreModuleName, "$$accountIdToStatuses");
        long aliceId = nameToUser.selectOne(Path.key("alice", "accountId"));
        long bobId = nameToUser.selectOne(Path.key("bob", "accountId"));

        Depot followAndBlockAccountDepot = ipc.clusterDepot(relationshipsModuleName, "*followAndBlockAccountDepot");
        followAndBlockAccountDepot.append(new FollowAccount(bobId, aliceId, ts+=1));

        for (long fooId : fooIds) {
            followAndBlockAccountDepot.append(new FollowAccount(bobId, fooId, ts+=1));
            followAndBlockAccountDepot.append(new FollowAccount(fooId, bobId, ts+=1));
            followAndBlockAccountDepot.append(new FollowAccount(fooId, aliceId, ts+=1));
        }

        Depot listDepot = ipc.clusterDepot(relationshipsModuleName, "*listDepot");
        PState authorIdToListIds = ipc.clusterPState(relationshipsModuleName, "$$authorIdToListIds");
        listDepot.append(new AccountList(bobId, "cool people", "list", ts+=1));
        Long bobsListId = authorIdToListIds.selectOne(Path.key(bobId).first());
        for (long fooId : fooIds) {
            listDepot.append(new AccountListMember(bobsListId, fooId, ts += 1));
        }

        Depot statusDepot = ipc.clusterDepot(Core.class.getName(), "*statusDepot");
        for (int i = 0; i < 50; i++) {
            ts += weekMillis / 50;
            statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(aliceId, StatusContent.normal(new NormalStatusContent(i + " Hello, world!", StatusVisibility.Public)), ts)));
            statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(bobId, StatusContent.normal(new NormalStatusContent(i + " #vanlife https://github.com", StatusVisibility.Public)), ts)));
            statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(aliceId, StatusContent.normal(new NormalStatusContent(i + " @bob this is a direct message", StatusVisibility.Direct)), ts)));
            long aliceDirect = accountIdToStatuses.selectOne(Path.key(aliceId).first().first());
            statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(bobId, StatusContent.reply(new ReplyStatusContent(i + " @alice this is also a direct message", StatusVisibility.Direct, new StatusPointer(aliceId, aliceDirect))), ts)));
            statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(aliceId, StatusContent.normal(new NormalStatusContent(i + " @bob this is a public message", StatusVisibility.Public)), ts)));
            long alicePublic = accountIdToStatuses.selectOne(Path.key(aliceId).first().first());
            statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(bobId, StatusContent.reply(new ReplyStatusContent(i + " @alice this is also a public message", StatusVisibility.Public, new StatusPointer(aliceId, alicePublic))), ts)));
        }

        Depot favoriteStatusDepot = ipc.clusterDepot(Core.class.getName(), "*favoriteStatusDepot");
        StatusQueryResults aliceTimeline = null;
        try {
            aliceTimeline = ApolloApiController.manager.getAccountTimeline(null, aliceId, null, null, false, true).get();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        StatusResultWithId aliceStatus = aliceTimeline.results.get(0);
        StatusPointer aliceStatusPointer = new StatusPointer(aliceId, aliceStatus.statusId);
        for (long fooId : fooIds) {
            favoriteStatusDepot.append(new FavoriteStatus(fooId, aliceStatusPointer, System.currentTimeMillis()));
            statusDepot.append(new BoostStatus(UUID.randomUUID().toString(), fooId, aliceStatusPointer, System.currentTimeMillis()));
        }

        return ipc;
    }

}