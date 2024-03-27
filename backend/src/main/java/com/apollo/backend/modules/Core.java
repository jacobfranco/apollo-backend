package com.apollo.backend.modules;

import com.apollo.backend.*;
import com.apollo.backend.data.*;
import com.apollo.backend.navs.*;

import com.rpl.rama.*;
import com.rpl.rama.helpers.*;
import com.rpl.rama.integration.TaskGlobalContext;
import com.rpl.rama.integration.TaskGlobalObject;
import com.rpl.rama.module.*;
import com.rpl.rama.ops.*;

import java.io.*;
import java.util.*;

import static com.apollo.backend.ApolloHelpers.extractFields;

import com.clearspring.analytics.stream.membership.BloomFilter;

public class Core implements RamaModule {

    public static final int DEFAULT_TIMELINE_MAX_AMOUNT = 600;

    // not constants so they can be changed in tests
  public int timelineMaxAmount = DEFAULT_TIMELINE_MAX_AMOUNT;

    // A bloom filter of all of an account's follows is used to reduce PState queries
  // when filtering replies during fanout. These are kept durably in a PState on this
  // module and also cached in memory.
  public static class RBloomFilter implements RamaSerializable {
    public BloomFilter bloom = new BloomFilter(500, 0.01);

    private void writeObject(ObjectOutputStream out) throws IOException {
      byte[] ser = BloomFilter.serialize(bloom);
      out.writeInt(ser.length);
      out.write(ser);
    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
      byte[] ser = new byte[in.readInt()];
      in.read(ser);
      bloom = BloomFilter.deserialize(ser);
    }
  }

    // This is the representation of each home timeline, kept in-memory on the module. To acheive fault-tolerance,
  // these are recomputed from scratch on read if the queried account's home timeline is missing. In this case
  // the home timeline is reconstructed by looking at recent statuses of the account's follows (see the "refreshHomeTimeline"
  // query topology below.)
  public static class Timeline {
    // To reduce memory usage and avoid GC pressure, the timeline is represented with an array of primitives.
    public long[] buffer = null;
    public int startIndex = 0; // index within buffer that contains oldest timeline element
    public int numElems = 0; // number of elements in this timeline
    public long startIndexTimelineIndex = Long.MAX_VALUE; // timeline index (always decreasing for every append) of startIndex
    private int _bufferAmount;
    public boolean isRefreshed = false;
    public long lastFetchAccountId = -1;
    public long lastFetchStatusId = -1;
    public long lastFetchTimelineIndex = -1;
    // This is where the bloom filter of an account's follows is cached.
    public RBloomFilter rbloom = null;

    public Timeline(int bufferAmount, boolean enableRefreshes) {
      _bufferAmount = bufferAmount;
      if(!enableRefreshes) buffer = new long[2*_bufferAmount];
    }

    public void addItem(long authorId, long statusId) {
      if(buffer==null) return;
      int targetIndex = (startIndex + 2 * numElems) % buffer.length;
      buffer[targetIndex] = authorId;
      buffer[targetIndex + 1] = statusId;
      if(numElems==_bufferAmount) {
        startIndex = (startIndex + 2) % buffer.length;
        startIndexTimelineIndex--;
      } else numElems++;
    }

    public void refreshStatuses(List<List> tuples) {
      if(tuples.size() > 0) {
        if(buffer==null) buffer = new long[2*_bufferAmount];
        List<StatusPointer> existing = readTimelineFrom(new StatusPointer(-1, -1), null, numElems);
        Set<StatusPointer> existingSet = new HashSet(existing);

        int numToAdd = _bufferAmount - numElems;

        List<List> appendable = new ArrayList();
        for(List tuple: tuples) {
          if(appendable.size() == numToAdd) break;
          if(!existingSet.contains(new StatusPointer((Long) tuple.get(1), (Long) tuple.get(2)))) appendable.add(tuple);
        }
        numElems = 0;
        startIndexTimelineIndex = Long.MAX_VALUE;
        lastFetchAccountId = -1;
        lastFetchStatusId = -1;
        lastFetchTimelineIndex = -1;
        for(int i=0; i<numToAdd && i<appendable.size(); i++) {
          List tuple = appendable.get(appendable.size() - 1 - i);
          addItem((Long) tuple.get(1), (Long) tuple.get(2));
        }
        Collections.reverse(existing);
        for(StatusPointer sp: existing) addItem(sp.authorId, sp.statusId);
        isRefreshed = true;
      }
    }

    // excludes the start
    public List<StatusPointer> readTimelineFrom(StatusPointer firstStatusPointer, StatusPointer endPointer, int maxAmt) {
      if(buffer==null) return new ArrayList();
      long timelineIndex = -1;
      // - this is an optimization to deal with mastodon/soapbox design of paginating timeline using status ID
      // instead of a timeline index
      // - for soapbox pagination always uses the last status pointer from the previous page
      // - the optimization here will fail if user has two clients open at once, so it falls back on a scan
      //   in this case
      // - the scan is only over 600 entries in memory, per page, so it's not bad
      if(firstStatusPointer.authorId == lastFetchAccountId && firstStatusPointer.statusId == lastFetchStatusId) {
        timelineIndex = lastFetchTimelineIndex;
      } else if(firstStatusPointer.statusId >= 0) {
        for(int i=0; i<numElems; i++) {
          int j = (startIndex + 2 * i) % buffer.length;
          if(buffer[j] == firstStatusPointer.authorId && buffer[j+1] == firstStatusPointer.statusId) {
            timelineIndex = startIndexTimelineIndex - i;
            break;
          }
        }
      }

      timelineIndex++; // to exclude the start
      List<StatusPointer> ret = new ArrayList();
      long distance = startIndexTimelineIndex - timelineIndex;
      if(distance >= 0)  {
        int startDistance = (int) Math.min((long)numElems-1, distance);
        int retrieveStartIndex = (startIndex + 2 * startDistance) % buffer.length;
        long retrieveStartTimelineIndex = startIndexTimelineIndex - startDistance;
        for(int i=0; i < maxAmt && i <= startDistance; i++) {
          int j = retrieveStartIndex - 2*i;
          if(j < 0) j = buffer.length + j;
          StatusPointer next = new StatusPointer(buffer[j], buffer[j+1]);
          if(next.equals(endPointer)) break;
          ret.add(next);
        }
        if(!ret.isEmpty() && endPointer != null) {
          StatusPointer sp = ret.get(ret.size() - 1);
          lastFetchAccountId = sp.authorId;
          lastFetchStatusId = sp.statusId;
          lastFetchTimelineIndex = retrieveStartTimelineIndex + ret.size() - 1;
        }
      }
      return ret;
    }
  }

    // This holds all in-memory home timelines for accounts on this partition of the module.
  // See the call to ".declareObject" below for how a separate instance of this is instantiated
  // on every task.
  public static class HomeTimelines implements TaskGlobalObject {
    public HashMap<Long, Timeline> timelines; // accountId to Timeline
    public HashSet<List> lastMicrobatchWrites; // contains tuples of [target, accountId, statusId]
    public Long lastMicrobatchId = null;
    private int _bufferAmount;
    private boolean _enableRefreshes;

    public HomeTimelines(int bufferAmount, boolean enableRefreshes) {
      _bufferAmount = bufferAmount;
      _enableRefreshes = enableRefreshes;
    }

    @Override
    public void prepareForTask(int taskId, TaskGlobalContext context) {
      timelines = new HashMap();
      lastMicrobatchWrites = new HashSet();
    }

    private Timeline getTimeline(long accountId) {
      Timeline timeline = timelines.get(accountId);
      if(timeline==null) {
        timeline = new Timeline(_bufferAmount, _enableRefreshes);
        timelines.put(accountId, timeline);
      }
      return timeline;
    }

    public Object addTimelineItem(Long targetId, StatusPointer pointer, Long microbatchId) {
      // ensures exactly-once write semantics for failed microbatches, and prevents
      // hashtag fanout and follower fanout from both writing the status to one follower's
      // timeline in same iteration
      if(microbatchId != lastMicrobatchId) {
        lastMicrobatchId = microbatchId;
        lastMicrobatchWrites = new HashSet();
      }
      List tuple = Arrays.asList(targetId, pointer.authorId, pointer.statusId);
      if(lastMicrobatchWrites.contains(tuple)) return null;
      else lastMicrobatchWrites.add(tuple);
      if(!_enableRefreshes || !needsRefresh(targetId)) getTimeline(targetId).addItem(pointer.authorId, pointer.statusId);
      return null;
    }

    public List<StatusPointer> readTimelineFrom(long accountId, StatusPointer firstStatusPointer, int maxAmt) {
      return getTimeline(accountId).readTimelineFrom(firstStatusPointer, null, maxAmt);
    }

    public List<StatusPointer> readTimelineUntil(long accountId, StatusPointer endStatusPointer, int maxAmt) {
      Timeline timeline = timelines.get(accountId);
      if(timeline==null) return new ArrayList();
      return timeline.readTimelineFrom(new StatusPointer(-1, -1), endStatusPointer, maxAmt);
    }

    public boolean needsRefresh(long accountId) {
      return !getTimeline(accountId).isRefreshed;
    }

    public Object refreshStatuses(long accountId, List<List> tuples) {
      getTimeline(accountId).refreshStatuses(tuples);
      return null;
    }

    public RBloomFilter getBloomFilter(long accountId) {
      return getTimeline(accountId).rbloom;
    }

    public Object setBloomFilter(long accountId, RBloomFilter rbloom) {
      getTimeline(accountId).rbloom = rbloom;
      return null;
    }

    @Override
    public void close() { }
  }

  /*
   * Accounts require low latency updates (a few millis) so streaming is used for
   * processing (instead
   * of microbatching). Streaming integrates with depot appends as well, allowing
   * for coordination of
   * updates with the frontend. Depot appends done with AckLevel.ACK (the default)
   * only return when
   * all colocated streaming topologies have finished processing the data in that
   * append. This is used
   * in the frontend so it knows when an account update has gone through (e.g. to
   * reload page or
   * re-enable a submit button).
   */
  private static void declareAccountsTopology(Topologies topologies) {
    StreamTopology stream = topologies.stream("accounts");
    ModuleUniqueIdPState accountIdGen = new ModuleUniqueIdPState("$$accountIdGen");
    accountIdGen.declarePState(stream);
    stream.pstate("$$nameToUser", PState.mapSchema(String.class,
        PState.fixedKeysSchema("accountId", Long.class,
            "uuid", String.class)));
    stream.pstate("$$accountIdToAccount", PState.mapSchema(Long.class, Account.class));

    /*
     * User registration does three things when that name is not already registered:
     * - generates a user id for that user
     * - updates $$nameToUser PState (which contains a mapping from name -> user id)
     * - updates $$accountIdToAccount PState (which maps user id to Account)
     * 
     * User registration is implemented to correctly handle:
     * - Concurrent registration of same name (first one wins)
     * - Failures of topology (e.g. a machine involved in the processing dies midway
     * through
     * processing). Streaming failures are handled by retrying from the start of the
     * topology.
     */
    stream.source("*accountDepot").out("*data")
        .macro(extractFields("*data", "*name", "*uuid"))
        .localSelect("$$nameToUser", Path.key("*name")).out("*currInfo")
        .each(Ops.GET, "*currInfo", "uuid").out("*currUUID")
        // By including a UUID with each registration request, we can distinguish
        // between:
        // - this name is already registered by a different request so we shouldn't
        // override it
        // - this name was registered by the same request, so we should continue
        // finishing the
        // registration
        .ifTrue(new Expr(Ops.OR, new Expr(Ops.IS_NULL, "*currInfo"),
            new Expr(Ops.EQUAL, "*uuid", "*currUUID")),
            Block.macro(accountIdGen.genId("*accountId"))
                .localTransform("$$nameToUser", Path.key("*name").multiPath(Path.key("accountId").termVal("*accountId"),
                    Path.key("uuid").termVal("*uuid")))
                .hashPartition("*accountId")
                .localTransform("$$accountIdToAccount", Path.key("*accountId").termVal("*data"))
                .invokeQuery("getAccountMetadata", null, "*accountId").out("*metadata")
                .each((RamaFunction3<Long, Account, AccountMetadata, AccountWithId>) AccountWithId::new, "*accountId",
                    "*data", "*metadata")
                .out("*accountWithId")
                .depotPartitionAppend("*accountWithIdDepot", "*accountWithId"));

    stream.source("*accountEditDepot", StreamSourceOptions.retryNone()).out("*editAccount")
        .macro(extractFields("*editAccount", "*accountId", "*edits"))
        .each(Ops.EXPLODE, "*edits").out("*edit")
        .each((EditAccountField editAccount, OutputCollector collector) -> {
          collector.emit(editAccount.getSetField().getFieldName(), editAccount.getFieldValue());
        }, "*edit").out("*fieldName", "*fieldValue")
        .localTransform("$$accountIdToAccount", Path.must("*accountId")
            .customNavBuilder(TField::new, "*fieldName")
            .termVal("*fieldValue"));
  }

  private void declareQueries(Topologies topologies) {

    // Begin defining a query topology named "getAccountTimeline" with inputs for
    // account IDs, starting status ID, a limit for the number of statuses, and a
    // flag for including replies.
    topologies.query("getAccountTimeline", "*requestAccountId", "*timelineAccountId", "*firstStatusId", "*limit",
        "*includeReplies").out("*results")
        // Partition the data based on the timeline account ID to ensure that all
        // operations related to a specific account are processed together.
        .hashPartition("*timelineAccountId")
        // For each status, generate sorted options based on the limit, excluding the
        // starting point to avoid duplicate fetching.
        .each((Integer limit) -> SortedRangeFromOptions.excludeStart().maxAmt(limit), "*limit").out("*sortedOptions")
        // Select statuses from the account's timeline that fall within the specified
        // range and sort options.
        .localSelect("$$accountIdToAccountTimeline", Path.key("*timelineAccountId")
            .sortedSetRangeFrom("*firstStatusId", "*sortedOptions")
            .all())
        .out("*statusId")
        // Fetch the first instance of each selected status from the statuses PState.
        .localSelect("$$accountIdToStatuses", Path.must("*timelineAccountId", "*statusId").first()).out("*status")

        // Check if each status should be excluded based on the "includeReplies"
        // parameter and the type of content.
        .macro(extractFields("*status", "*content"))
        // Determine if replies should be excluded, and set a flag accordingly.
        .each(Ops.IDENTITY, new Expr(Ops.AND, new Expr(Ops.NOT, "*includeReplies"),
            new Expr(Ops.IS_INSTANCE_OF, ReplyStatusContent.class, "*content")))
        .out("*shouldExclude")

        // Create a status pointer for each status, marking it to be excluded or
        // included in the final results based on the earlier step.
        .each(
            (Long authorId, Long statusId, Boolean shouldExclude) -> new StatusPointer(authorId, statusId)
                .setShouldExclude(shouldExclude),
            "*timelineAccountId", "*statusId", "*shouldExclude")
        .out("*statusPointer")

        // Return to the original partitioning scheme before aggregation.
        .originPartition()
        // Aggregate all status pointers into a list for further processing.
        .agg(Agg.list("*statusPointer")).out("*statusPointers")
        // Prepare query filter options for fetching statuses; in this case, filtering
        // is turned off.
        .each(() -> new QueryFilterOptions(FilterContext.Account, false)).out("*filterOptions")
        // Invoke another query, "getStatusesFromPointers", to fetch the actual status
        // objects based on the list of pointers and filter options.
        .invokeQuery("getStatusesFromPointers", "*requestAccountId", "*statusPointers", "*filterOptions")
        .out("*statusQueryResults")
        // Update the status query results based on the retrieved data, considering
        // pagination and whether the data is refreshed.
        .each(ApolloHelpers::updateStatusQueryResults, "*statusQueryResults", "*statusPointers", "*limit", false)
        .out("*results");

    // Refreshes the home timeline for a given account. This includes fetching new
    // statuses from followers and self, potentially merging these two sources, and
    // applying visibility and content filters.
    topologies.query("refreshHomeTimeline", "*accountId").out("*ret")
        // Set a reference point in the dataflow that can be returned to.
        .anchor("RefreshRoot")

        // Select data from a specific PState based on accountId, transforming the data
        // as specified.
        .select("$$followerToFolloweesById", Path.key("*accountId").sortedMapRangeFrom(0L, 300).mapVals()).out("*followeeFollower")
        // For each element in the collection, apply a function, outputting results to
        // specified fields.
        .each((Follower f) -> f.accountId, "*followeeFollower").out("*followee")
        // Apply a predefined macro to transform the data, extracting specified fields.
        .macro(extractFields("*followeeFollower", "*showBoosts"))
        .anchor("RefreshFollowers")

        // Return to previously set anchor point to continue data processing.
        .hook("RefreshRoot")
        // Apply a function to each item in the input collection without changing it,
        // outputting results.
        .each(Ops.IDENTITY, "*accountId").out("*followee")
        // Apply a function to each item to evaluate a boolean condition, outputting the
        // result.
        .each(Ops.IDENTITY, true).out("*showBoosts")
        .anchor("RefreshSelf")

        // Combine data streams from specified anchors, merging their outputs.
        .unify("RefreshFollowers", "RefreshSelf")
        // Select data from a PState based on a followee's ID, applying transformations
        // as specified.
        .select("$$accountIdToStatuses",
            Path.key("*followee").sortedMapRangeFrom(0L, 30).transformed(Path.mapVals().term(Ops.FIRST)))
        .out("*statusMap")
        // For each map entry, output keys and values to specified fields.
        .each(Ops.EXPLODE_MAP, "*statusMap").out("*statusId", "*status")
        // Apply a macro to extract specified fields from each item in the collection.
        .macro(extractFields("*status", "*content", "*timestamp"))
        // Process content based on specific conditions, filtering and transforming as
        // specified.
        .subSource("*content",
            SubSource.create(ReplyStatusContent.class).keepTrue(false),
            SubSource.create(BoostStatusContent.class).keepTrue("*showBoosts"),
            SubSource.create(NormalStatusContent.class)
                .macro(extractFields("*content", "*visibility"))
                .keepTrue(new Expr(Ops.NOT_EQUAL, "*visibility", StatusVisibility.Direct)))
        // Combine multiple fields into a tuple, outputting the result.
        .each(Ops.TUPLE, "*timestamp", "*followee", "*statusId").out("*tuple")
        // Assigns data to the original partition for processing.
        .originPartition()
        // Aggregates data based on specified criteria, sorting and limiting the output.
        .agg(Agg.topMonotonic(timelineMaxAmount, "*tuple").sortValFunction(Ops.FIRST)).out("*toAdd")
        // Applies a function to update home timelines based on aggregated data.
        .each(HomeTimelines::refreshStatuses, "*homeTimelines", "*accountId", "*toAdd").out("*ret");

    // Define a query topology named "getAccountMetadata" with input parameters for
    // the requester's account ID and the target account ID.
    topologies.query("getAccountMetadata", "*requestAccountId", "*accountId").out("*result")
        // Distribute the processing of this query based on the "*accountId" to ensure
        // that all data related to a specific account is processed together.
        .hashPartition("*accountId")
        // Select the total count of statuses for the target account by accessing a
        // predefined state or structure "$$accountIdToAccountTimeline"
        // and viewing its size. The result is output to "*statusCount".
        .localSelect("$$accountIdToAccountTimeline", Path.key("*accountId").view(Ops.SIZE)).out("*statusCount")
        // Select the last status posted by the target account from
        // "$$accountIdToStatuses" by navigating through its structure and
        // fetching the first element. The result is output to "*lastStatus".
        .localSelect("$$accountIdToStatuses",
            Path.key("*accountId").subselect(Path.first().last().first()).view(Ops.FIRST))
        .out("*lastStatus")
        // Determine if the requester follows the target account and count the total
        // number of followers for the target account.
        // This is done by selecting data from "$$followeeToFollowers" using the target
        // account ID and evaluating if the requester is a follower,
        // and counting the total followers. The results are output to "*followerTuple".
        .select("$$followeeToFollowers", Path.key("*accountId")
            .subselect(Path.multiPath(Path.view(Ops.CONTAINS, "*requestAccountId"),
                Path.view(Ops.SIZE))))
        .out("*followerTuple")
        // Expand the tuple obtained in the previous step to separate values indicating
        // if the requester is a follower ("*isFollowedByRequester")
        // and the total number of followers ("*followerCount").
        .each(Ops.EXPAND, "*followerTuple").out("*isFollowedByRequester", "*followerCount")
        // Similar to the follower count, determine if the target account follows the
        // requester and count the total number of accounts
        // the target account is following. This uses "$$followerToFollowees" in a
        // similar manner and outputs the results to "*followeeTuple".
        .select("$$followerToFollowees", Path.key("*accountId")
            .subselect(Path.multiPath(Path.view(Ops.CONTAINS, "*requestAccountId"),
                Path.view(Ops.SIZE))))
        .out("*followeeTuple")
        // Expand the "*followeeTuple" to obtain separate values indicating if the
        // target account is following the requester ("*isFollowingRequester")
        // and the total number of followees ("*followeeCount").
        .each(Ops.EXPAND, "*followeeTuple").out("*isFollowingRequester", "*followeeCount")
        // Combine all the extracted and calculated information to construct the final
        // account metadata. This includes the counts of statuses,
        // followers, and followees, as well as boolean flags indicating mutual follow
        // status. If there's a last status, its timestamp is added to the metadata.
        .each((Integer statusCount, Integer followerCount, Integer followeeCount, Boolean isFollowedByRequester,
            Boolean isFollowingRequester, Status lastStatus) -> {
          AccountMetadata metadata = new AccountMetadata(statusCount, followerCount, followeeCount,
              isFollowedByRequester, isFollowingRequester);
          if (lastStatus != null)
            metadata.setLastStatusTimestamp(lastStatus.timestamp);
          return metadata;
        },
            "*statusCount", "*followerCount", "*followeeCount", "*isFollowedByRequester", "*isFollowingRequester",
            "*lastStatus")
        .out("*result")
        // Return processing to the original partitioning scheme.
        .originPartition();

  }

  @Override
  public void define(Setup setup, Topologies topologies) {

    // Depots
    setup.declareDepot("*accountDepot", Depot.hashBy(ApolloHelpers.ExtractName.class));
    setup.declareDepot("*accountEditDepot", Depot.hashBy(ApolloHelpers.ExtractAccountId.class));
    setup.declareDepot("*accountWithIdDepot", Depot.disallow());

    // Topologies
    declareAccountsTopology(topologies);

    // Queries
    declareQueries(topologies);

  }

}