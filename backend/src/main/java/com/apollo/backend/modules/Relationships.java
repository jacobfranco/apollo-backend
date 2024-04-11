package com.apollo.backend.modules;

import com.apollo.backend.data.*;

import com.rpl.rama.*;
import com.rpl.rama.helpers.KeyToLinkedEntitySetPStateGroup;
import com.rpl.rama.module.StreamSourceOptions;
import com.rpl.rama.module.StreamTopology;
import com.rpl.rama.ops.*;

import java.util.*;

import static com.apollo.backend.ApolloHelpers.*;

import com.apollo.backend.ApolloHelpers.ExtractAccountId;
import com.apollo.backend.ApolloHelpers.ExtractCode;

public class Relationships implements RamaModule {

  public int relationshipCountLimit = 1000000; // total number of follows/blocks/mutes
  public int newTaskCutoffLimit = 1000;

  public static boolean notEmpty(List l) {
    return !l.isEmpty();
  }

  public static boolean shouldMute(MuteAccountOptions options, boolean notifications) {
    return options != null && (!notifications || options.muteNotifications);
  }

  /*
   * This helper defines a path for a common need throughout the implementation to
   * know if someone is
   * blocked or muted by another user.
   */
  public static Path.Impl isBlockedOrMutedPath(Object accountId, Object targetId, boolean notifications) {
    return Path.key(accountId)
        .subselect(Path.multiPath(
            Path.key("muted", targetId).view(Relationships::shouldMute, notifications).filterPred(Ops.IDENTITY),
            Path.key("blocked").setElem(targetId)))
        .view(Relationships::notEmpty);
  }

  private static Follower makeFollower(long accountId, Boolean showBoosts, List<String> languages,
      Follower existingFollower) {
    Follower follower = existingFollower == null ? new Follower(accountId, true) : existingFollower;
    if (showBoosts != null)
      follower.setShowBoosts(showBoosts);
    if (languages != null)
      follower.setLanguages(languages);
    return follower;
  }

  /*
   * This defines most of the functionality for this module that requires
   * millisecond-level
   * update latencies. It defines them in a single stream topology, but these
   * could also
   * be broken up into multiple stream topologies.
   */
  private void declareStreamTopology(Topologies topologies) {
    StreamTopology stream = topologies.stream("relationshipsStream");

    // filter pstates
    stream.pstate("$$accountIdToFilterIdToFilter",
        PState.mapSchema(Long.class, PState.mapSchema(Long.class, Filter.class)));

    // account id -> requesting account ids
    KeyToLinkedEntitySetPStateGroup accountIdToFollowRequests = new KeyToLinkedEntitySetPStateGroup(
        "$$accountIdToFollowRequests", Long.class, FollowLockedAccount.class)
        .entityIdFunction(Long.class, req -> ((FollowLockedAccount) req).requesterId)
        .descending();
    accountIdToFollowRequests.declarePStates(stream);

    // In this case the account ID in Follower is the account being followed, and
    // the other fields are about the follower.
    // This PState supports:
    // - Getting an account's follows in order in which they were followed
    // (paginated)
    // - Getting number of follows for an account
    // - Querying whether user A follows user B
    KeyToLinkedEntitySetPStateGroup followerToFollowees = new KeyToLinkedEntitySetPStateGroup("$$followerToFollowees",
        Long.class, Follower.class)
        .entityIdFunction(Long.class, new ExtractAccountId())
        .descending();

    // This PState supports:
    // - Getting an account's followers in order in which they followed (paginated)
    // - Getting number of followers for an account
    // - Querying whether user A follows user B. Note that this query can be
    // answered
    // equally well by either this PState or followerToFollowees.
    KeyToLinkedEntitySetPStateGroup followeeToFollowers = new KeyToLinkedEntitySetPStateGroup("$$followeeToFollowers",
        Long.class, Follower.class)
        .entityIdFunction(Long.class, new ExtractAccountId())
        .descending();
    followerToFollowees.declarePStates(stream);
    followeeToFollowers.declarePStates(stream);

    // The next three PStates define an additional view of the social graph to
    // balance processing as much as possible
    // during fanout even if the social graph is extremely unbalanced (e.g. some
    // users having 20M followers while the average
    // is 400). How this works is explained more below.

    // account ID to list of account IDs on which partitioned followers are kept
    stream.pstate("$$partitionedFollowersControl", PState.mapSchema(Long.class, List.class));
    // account ID -> follower ID -> task
    stream.pstate("$$partitionedFollowerTasks", PState.mapSchema(Long.class,
    PState.mapSchema(Long.class, Integer.class).subindexed())); 

    stream.pstate("$$partitionedFollowers",
        PState.mapSchema(Long.class, PState.mapSchema(Long.class, Follower.class).subindexed()));

    // We keep an extra PState tracking only followers who've requested
    // notifications for the notifications module
    stream.pstate("$$followeeToNotifiedFollowerIds",
        PState.mapSchema(Long.class, PState.setSchema(Long.class).subindexed()));

    stream.pstate("$$accountIdToSuppressions",
        PState.mapSchema(Long.class,
            PState.fixedKeysSchema("muted", PState.mapSchema(Long.class, MuteAccountOptions.class).subindexed(),
                "blocked", PState.setSchema(Long.class).subindexed())));

    stream.pstate("$$hashtagToFollowers", PState.mapSchema(String.class, PState.setSchema(Long.class).subindexed()));
    stream.source("*followAndBlockAccountDepot", StreamSourceOptions.retryAllAfter()).out("*initialData")
        .anchor("SocialGraphRoot")
        .each(Ops.IDENTITY, "*initialData").out("*data")
        .anchor("Normal")

        .hook("SocialGraphRoot")
        .keepTrue(new Expr(Ops.IS_INSTANCE_OF, BlockAccount.class, "*initialData"))
        .each((BlockAccount data, OutputCollector collector) -> {
          collector.emit(new RemoveFollowAccount(data.getAccountId(), data.getTargetId(), data.getTimestamp()));
          collector.emit(new RemoveFollowAccount(data.getTargetId(), data.getAccountId(), data.getTimestamp()));
          collector.emit(new RejectFollowRequest(data.getAccountId(), data.getTargetId()));
        }, "*initialData").out("*data")
        .anchor("ImplicitUnfollow")

        .hook("SocialGraphRoot")
        .keepTrue(new Expr(Ops.IS_INSTANCE_OF, AcceptFollowRequest.class, "*initialData"))
        .macro(extractFields("*initialData", "*accountId", "*requesterId"))
        .localSelect("$$accountIdToFollowRequests", Path.key("*accountId").must("*requesterId")).out("*followRequestId")
        .localSelect("$$accountIdToFollowRequestsById", Path.key("*accountId", "*followRequestId"))
        .out("*followRequest")
        // FollowAccount.targetId is equivalent to FollowAccountRequest.accountId
        // so we need to partition for the implicit event
        .hashPartition("*requesterId")
        .each((AcceptFollowRequest data, FollowLockedAccount req) -> {
          FollowAccount follow = new FollowAccount(data.getRequesterId(), data.getAccountId(), data.getTimestamp());
          if (req.isSetShowBoosts())
            follow.setShowBoosts(req.showBoosts);
          if (req.isSetNotify())
            follow.setNotify(req.notify);
          if (req.isSetLanguages())
            follow.setLanguages(req.languages);
          return follow;
        }, "*initialData", "*followRequest").out("*data")
        .anchor("CompleteFollowRequest")

        .hook("SocialGraphRoot")
        .keepTrue(new Expr(Ops.IS_INSTANCE_OF, FollowLockedAccount.class, "*initialData"))
        .macro(extractFields("*initialData", "*accountId", "*requesterId"))
        .localSelect("$$followeeToFollowers", Path.key("*accountId", "*requesterId")).out("*existingFollowerId")
        // Existing relationship means we do NOT need to create a follow request, just
        // update settings.
        .ifTrue(new Expr(Ops.IS_NOT_NULL, "*existingFollowerId"),
            Block.each((FollowLockedAccount data) -> {
              FollowAccount follow = new FollowAccount(data.requesterId, data.accountId, data.timestamp);
              if (data.isSetShowBoosts())
                follow.setShowBoosts(data.isShowBoosts());
              if (data.isSetNotify())
                follow.setNotify(data.isNotify());
              if (data.isSetLanguages())
                follow.setLanguages(data.getLanguages());
              return follow;
            }, "*initialData").out("*data")
                .hashPartition("*requesterId")
                .anchor("UpdatePrivateFollow"),
            Block.macro(extractFields("*initialData", "*accountId", "*requesterId"))
                .macro(accountIdToFollowRequests.addToLinkedSet("*accountId", "*initialData")))

        .unify("Normal", "ImplicitUnfollow", "CompleteFollowRequest", "UpdatePrivateFollow")
        .subSource("*data",
            SubSource.create(FollowAccount.class)
                .macro(extractFields("*data", "*accountId", "*targetId", "*showBoosts", "*notify", "*languages"))
                .localSelect("$$followerToFollowees", Path.key("*accountId").view(Ops.SIZE)).out("*followeeCount")
                .keepTrue(new Expr(Ops.LESS_THAN, "*followeeCount", relationshipCountLimit))
                .localSelect("$$followerToFollowees", Path.key("*accountId", "*targetId")).out("*followeeId")
                .localSelect("$$followerToFolloweesById", Path.key("*accountId", "*followeeId"))
                .out("*existingFollowee")
                .each(Relationships::makeFollower, "*targetId", "*showBoosts", "*languages", "*existingFollowee")
                .out("*followee")
                .macro(followerToFollowees.addToLinkedSet("*accountId", "*followee"))
                .hashPartition("*targetId")
                .localSelect("$$followeeToFollowers", Path.key("*targetId", "*accountId")).out("*followerId")
                .localSelect("$$followeeToFollowersById", Path.key("*targetId", "*followerId")).out("*existingFollower")
                .each(Relationships::makeFollower, "*accountId", "*showBoosts", "*languages", "*existingFollower")
                .out("*follower")
                .macro(followeeToFollowers.addToLinkedSet("*targetId", "*follower"))
                .macro(accountIdToFollowRequests.removeFromLinkedSetByEntityId("*targetId", "*accountId"))
                // we only want to update the notify preference if it has been explicitly
                // supplied
                .ifTrue(new Expr(Ops.IS_NOT_NULL, "*notify"),
                    Block.ifTrue("*notify",
                        Block.localTransform("$$followeeToNotifiedFollowerIds",
                            Path.key("*targetId").voidSetElem().termVal("*accountId")),
                        Block.localTransform("$$followeeToNotifiedFollowerIds",
                            Path.key("*targetId").setElem("*accountId").termVoid())))

                // This block of code creates a view of the social graph that will balance
                // processing for fanout
                // regardless of how unbalanced the social graph is. It works by spreading an
                // account's followers
                // across many partitions of the module. Every 1000 followers a new partition is
                // chosen for the
                // next 1000 followers until all tasks have a partition for this account. After
                // that, new followers
                // are assigned in round-robin order to all partitions.
                .localSelect("$$partitionedFollowerTasks", Path.key("*targetId", "*accountId")).out("*currTask")
                .ifTrue(new Expr(Ops.IS_NOT_NULL, "*currTask"),
                    Block.each(Ops.IDENTITY, "*currTask").out("*targetTask"),
                    Block.localSelect("$$partitionedFollowersControl", Path.key("*targetId")).out("*tasks")
                        .localSelect("$$partitionedFollowerTasks", Path.key("*targetId").view(Ops.SIZE))
                        .out("*followersCount")
                        .each((Integer numFollowers, ModuleInstanceInfo info, Integer cutoff) -> {
                          if (numFollowers >= info.getNumTasks() * cutoff)
                            return "r";
                          else if (numFollowers % cutoff == 0)
                            return "n";
                          else
                            return null;
                        }, "*followersCount", new Expr(Ops.MODULE_INSTANCE_INFO), newTaskCutoffLimit)
                        .out("*targetTaskSwitch")
                        .ifTrue(new Expr(Ops.EQUAL, "n", "*targetTaskSwitch"),
                            Block.each((ArrayList tasks, Integer currentTaskId, ModuleInstanceInfo info) -> {
                              if (tasks == null) {
                                tasks = new ArrayList();
                                tasks.add(currentTaskId);
                              } else {
                                Set currTasks = new HashSet(tasks);
                                List<Integer> availableTasks = new ArrayList();
                                for (int i = 0; i < info.getNumTasks(); i++) {
                                  if (!currTasks.contains(i))
                                    availableTasks.add(i);
                                }
                                tasks.add(availableTasks.get(new Random().nextInt(availableTasks.size())));
                              }
                              return tasks;
                            }, "*tasks", new Expr(Ops.CURRENT_TASK_ID), new Expr(Ops.MODULE_INSTANCE_INFO))
                                .out("*newTasks")
                                .each(Ops.LAST, "*newTasks").out("*targetTask")
                                .localTransform("$$partitionedFollowersControl",
                                    Path.key("*targetId").termVal("*newTasks")),
                            Block.ifTrue(new Expr(Ops.EQUAL, "r", "*targetTaskSwitch"),
                                Block
                                    .each((List tasks, Integer followersCount) -> tasks
                                        .get(followersCount % tasks.size()), "*tasks", "*followersCount")
                                    .out("*targetTask"),
                                Block.each(Ops.LAST, "*tasks").out("*targetTask")))
                        .localTransform("$$partitionedFollowerTasks",
                            Path.key("*targetId", "*accountId").termVal("*targetTask")))
                .directPartition("*targetTask")
                .localTransform("$$partitionedFollowers", Path.key("*targetId", "*accountId").termVal("*follower")),
            SubSource.create(RemoveFollowAccount.class)
                .macro(extractFields("*data", "*accountId", "*targetId", "*followerSharedInboxUrl"))
                .hashPartition("*accountId")
                .macro(followerToFollowees.removeFromLinkedSetByEntityId("*accountId", "*targetId"))
                .hashPartition("*targetId")
                .macro(followeeToFollowers.removeFromLinkedSetByEntityId("*targetId", "*accountId"))
                // Unfollowing also removes any pending follow request that exists.
                .macro(accountIdToFollowRequests.removeFromLinkedSetByEntityId("*targetId", "*accountId"))
                .localTransform("$$followeeToNotifiedFollowerIds",
                    Path.key("*targetId").setElem("*accountId").termVoid())
                .localSelect("$$partitionedFollowerTasks", Path.key("*targetId").must("*accountId")).out("*task")
                .directPartition("*task")
                .localTransform("$$partitionedFollowers", Path.key("*targetId", "*accountId").termVoid()),

            // handled above
            SubSource.create(FollowLockedAccount.class),

            // The actual following is handled by the implicit
            // event above, so all that's left to do is remove
            // the request. This also happens in FollowAccount's
            // handler, because we need to make sure the
            // FollowAccount event gets generated before we
            // remove the follow request.
            SubSource.create(AcceptFollowRequest.class),

            SubSource.create(RejectFollowRequest.class)
                .macro(extractFields("*data", "*accountId", "*requesterId"))
                .macro(accountIdToFollowRequests.removeFromLinkedSetByEntityId("*accountId", "*requesterId")),

            SubSource.create(BlockAccount.class)
                .macro(extractFields("*data", "*accountId", "*targetId", "*timestamp"))
                .localSelect("$$accountIdToSuppressions", Path.key("*accountId", "blocked").view(Ops.SIZE))
                .out("*blockeeCount")
                .keepTrue(new Expr(Ops.LESS_THAN, "*blockeeCount", relationshipCountLimit))
                .localTransform("$$accountIdToSuppressions",
                    Path.key("*accountId", "blocked").voidSetElem().termVal("*targetId")),

            SubSource.create(RemoveBlockAccount.class)
                .macro(extractFields("*data", "*accountId", "*targetId"))
                .localTransform("$$accountIdToSuppressions",
                    Path.key("*accountId", "blocked").setElem("*targetId").termVoid()));
  }

  @Override
  public void define(Setup setup, Topologies topologies) {

    setup.declareDepot("*authCodeDepot", Depot.hashBy(ExtractCode.class));
    setup.declareDepot("*followAndBlockAccountDepot", Depot.hashBy(ExtractAccountId.class));

    declareStreamTopology(topologies);
  }

}