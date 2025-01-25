package com.apollo.backend.modules;

import com.apollo.backend.*;
import com.apollo.backend.data.*;
import com.apollo.backend.navs.*;

import com.rpl.rama.*;
import com.rpl.rama.helpers.*;
import com.rpl.rama.integration.*;
import com.rpl.rama.module.*;
import com.rpl.rama.ops.*;

import clojure.lang.*;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

import org.apache.thrift.TBase;

import static com.apollo.backend.ApolloHelpers.extractFields;

import com.clearspring.analytics.stream.membership.BloomFilter;

/*
 * This module implements the main parts of Mastodon – timelines, statuses, and profiles. They're
 * kept colocated together because the most important part of the app, rendering a page of a home timeline,
 * needs to fetch a lot of timeline, status, and profile data at the same time. Keeping them colocated
 * increases the efficiency of these queries and lowers the latency.
 */

public class Core implements RamaModule {

        private static final int DESCENDANT_SEARCH_LIMIT = 5000;
        public static final int PINNED_STATUS_MAX_AMOUNT = 5; // TODO: Maybe change
        public static final int DEFAULT_TIMELINE_MAX_AMOUNT = 600;

        // not constants so they can be changed in tests
        public int timelineMaxAmount = DEFAULT_TIMELINE_MAX_AMOUNT;
        public boolean enableHomeTimelineRefresh = true;
        public int singlePartitionFanoutLimit = 10000;
        public int rangeQueryLimit = 1000;
        public int maxEditCount = 100;
        public int scheduledStatusTickMillis = 30000;

        // A bloom filter of all of an account's follows is used to reduce PState
        // queries
        // when filtering replies during fanout. These are kept durably in a PState on
        // this
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

        // This is the representation of each home timeline, kept in-memory on the
        // module. To acheive fault-tolerance,
        // these are recomputed from scratch on read if the queried account's home
        // timeline is missing. In this case
        // the home timeline is reconstructed by looking at recent statuses of the
        // account's follows (see the "refreshHomeTimeline"
        // query topology below.)
        public static class Timeline {
                // To reduce memory usage and avoid GC pressure, the timeline is represented
                // with an array of primitives.
                public long[] buffer = null;
                public int startIndex = 0; // index within buffer that contains oldest timeline element
                public int numElems = 0; // number of elements in this timeline
                public long startIndexTimelineIndex = Long.MAX_VALUE; // timeline index (always decreasing for every
                                                                      // append) of
                                                                      // startIndex
                private int _bufferAmount;
                public boolean isRefreshed = false;
                public long lastFetchAccountId = -1;
                public long lastFetchStatusId = -1;
                public long lastFetchTimelineIndex = -1;
                // This is where the bloom filter of an account's follows is cached.
                public RBloomFilter rbloom = null;

                public Timeline(int bufferAmount, boolean enableRefreshes) {
                        _bufferAmount = bufferAmount;
                        if (!enableRefreshes)
                                buffer = new long[2 * _bufferAmount];
                }

                public void addItem(long authorId, long statusId) {
                        if (buffer == null)
                                return;
                        int targetIndex = (startIndex + 2 * numElems) % buffer.length;
                        buffer[targetIndex] = authorId;
                        buffer[targetIndex + 1] = statusId;
                        if (numElems == _bufferAmount) {
                                startIndex = (startIndex + 2) % buffer.length;
                                startIndexTimelineIndex--;
                        } else
                                numElems++;
                }

                public void refreshStatuses(List<List> tuples) {
                        if (tuples.size() > 0) {
                                if (buffer == null)
                                        buffer = new long[2 * _bufferAmount];
                                List<StatusPointer> existing = readTimelineFrom(new StatusPointer(-1, -1), null,
                                                numElems);
                                Set<StatusPointer> existingSet = new HashSet(existing);

                                int numToAdd = _bufferAmount - numElems;

                                List<List> appendable = new ArrayList();
                                for (List tuple : tuples) {
                                        if (appendable.size() == numToAdd)
                                                break;
                                        if (!existingSet.contains(
                                                        new StatusPointer((Long) tuple.get(1), (Long) tuple.get(2))))
                                                appendable.add(tuple);
                                }
                                numElems = 0;
                                startIndexTimelineIndex = Long.MAX_VALUE;
                                lastFetchAccountId = -1;
                                lastFetchStatusId = -1;
                                lastFetchTimelineIndex = -1;
                                for (int i = 0; i < numToAdd && i < appendable.size(); i++) {
                                        List tuple = appendable.get(appendable.size() - 1 - i);
                                        addItem((Long) tuple.get(1), (Long) tuple.get(2));
                                }
                                Collections.reverse(existing);
                                for (StatusPointer sp : existing)
                                        addItem(sp.authorId, sp.statusId);
                                isRefreshed = true;
                        }
                }

                // excludes the start
                public List<StatusPointer> readTimelineFrom(StatusPointer firstStatusPointer, StatusPointer endPointer,
                                int maxAmt) {
                        if (buffer == null)
                                return new ArrayList();
                        long timelineIndex = -1;
                        // - this is an optimization to deal with Apollo/soapbox design of paginating
                        // timeline using status ID
                        // instead of a timeline index
                        // - for soapbox pagination always uses the last status pointer from the
                        // previous page
                        // - the optimization here will fail if user has two clients open at once, so it
                        // falls back on a scan
                        // in this case
                        // - the scan is only over 600 entries in memory, per page, so it's not bad
                        if (firstStatusPointer.authorId == lastFetchAccountId
                                        && firstStatusPointer.statusId == lastFetchStatusId) {
                                timelineIndex = lastFetchTimelineIndex;
                        } else if (firstStatusPointer.statusId >= 0) {
                                for (int i = 0; i < numElems; i++) {
                                        int j = (startIndex + 2 * i) % buffer.length;
                                        if (buffer[j] == firstStatusPointer.authorId
                                                        && buffer[j + 1] == firstStatusPointer.statusId) {
                                                timelineIndex = startIndexTimelineIndex - i;
                                                break;
                                        }
                                }
                        }

                        timelineIndex++; // to exclude the start
                        List<StatusPointer> ret = new ArrayList();
                        long distance = startIndexTimelineIndex - timelineIndex;
                        if (distance >= 0) {
                                int startDistance = (int) Math.min((long) numElems - 1, distance);
                                int retrieveStartIndex = (startIndex + 2 * startDistance) % buffer.length;
                                long retrieveStartTimelineIndex = startIndexTimelineIndex - startDistance;
                                for (int i = 0; i < maxAmt && i <= startDistance; i++) {
                                        int j = retrieveStartIndex - 2 * i;
                                        if (j < 0)
                                                j = buffer.length + j;
                                        StatusPointer next = new StatusPointer(buffer[j], buffer[j + 1]);
                                        if (next.equals(endPointer))
                                                break;
                                        ret.add(next);
                                }
                                if (!ret.isEmpty() && endPointer != null) {
                                        StatusPointer sp = ret.get(ret.size() - 1);
                                        lastFetchAccountId = sp.authorId;
                                        lastFetchStatusId = sp.statusId;
                                        lastFetchTimelineIndex = retrieveStartTimelineIndex + ret.size() - 1;
                                }
                        }
                        return ret;
                }
        }

        // This holds all in-memory home timelines for accounts on this partition of the
        // module.
        // See the call to ".declareObject" below for how a separate instance of this is
        // instantiated
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
                        if (timeline == null) {
                                timeline = new Timeline(_bufferAmount, _enableRefreshes);
                                timelines.put(accountId, timeline);
                        }
                        return timeline;
                }

                public Object addTimelineItem(Long targetId, StatusPointer pointer, Long microbatchId) {
                        // ensures exactly-once write semantics for failed microbatches, and prevents
                        // hashtag fanout and follower fanout from both writing the status to one
                        // follower's
                        // timeline in same iteration
                        if (microbatchId != lastMicrobatchId) {
                                lastMicrobatchId = microbatchId;
                                lastMicrobatchWrites = new HashSet();
                        }
                        List tuple = Arrays.asList(targetId, pointer.authorId, pointer.statusId);
                        if (lastMicrobatchWrites.contains(tuple))
                                return null;
                        else
                                lastMicrobatchWrites.add(tuple);
                        if (!_enableRefreshes || !needsRefresh(targetId))
                                getTimeline(targetId).addItem(pointer.authorId, pointer.statusId);
                        return null;
                }

                public List<StatusPointer> readTimelineFrom(long accountId, StatusPointer firstStatusPointer,
                                int maxAmt) {
                        return getTimeline(accountId).readTimelineFrom(firstStatusPointer, null, maxAmt);
                }

                public List<StatusPointer> readTimelineUntil(long accountId, StatusPointer endStatusPointer,
                                int maxAmt) {
                        Timeline timeline = timelines.get(accountId);
                        if (timeline == null)
                                return new ArrayList();
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
                public void close() {
                }
        }

        private static RBloomFilter initBloom(RBloomFilter rbloom) {
                return rbloom == null ? new RBloomFilter() : rbloom;
        }

        public static Block fetchBloomMacro(String accountIdVar, String outVar) {
                return Block.each(HomeTimelines::getBloomFilter, "*homeTimelines", accountIdVar).out(outVar)
                                .ifTrue(new Expr(Ops.IS_NULL, outVar),
                                                Block.localSelect("$$followsBloom",
                                                                Path.key(accountIdVar).view(Core::initBloom))
                                                                .out(outVar)
                                                                .each(HomeTimelines::setBloomFilter, "*homeTimelines",
                                                                                accountIdVar, outVar),
                                                Block.each(Ops.IDENTITY, outVar).out(outVar));
        }

        // This topology maintains the follow bloom filters for each account.
        private void declareFollowsBloomFiltersTopology(Topologies topologies) {
                MicrobatchTopology mb = topologies.microbatch("bloom");
                mb.pstate("$$followsBloom", PState.mapSchema(Long.class, RBloomFilter.class));

                mb.source("*followAndBlockAccountDepot").out("*microbatch")
                                .batchBlock(
                                                Block.explodeMicrobatch("*microbatch").out("*data")
                                                                .keepTrue(new Expr(Ops.IS_INSTANCE_OF,
                                                                                FollowAccount.class, "*data"))
                                                                .macro(extractFields("*data", "*accountId",
                                                                                "*targetId"))
                                                                // When generating a social graph from scratch to do
                                                                // load testing, it's
                                                                // much more efficient to do many adds to a bloom filter
                                                                // at the same time
                                                                // rather than read and write them from the PState for
                                                                // every follow.
                                                                .groupBy("*accountId",
                                                                                Block.agg(Agg.set("*targetId"))
                                                                                                .out("*adds"))
                                                                .macro(fetchBloomMacro("*accountId", "*rbloom"))
                                                                .each((RBloomFilter rbloom, Set<Long> accountIds) -> {
                                                                        for (Long accountId : accountIds)
                                                                                rbloom.bloom.add("" + accountId);
                                                                        return null;
                                                                }, "*rbloom", "*adds")
                                                                .localTransform("$$followsBloom", Path.key("*accountId")
                                                                                .termVal("*rbloom")));
        }

        public static Object lastItem(Object c, boolean isMap) {
                return isMap ? ((SortedMap) c).lastKey() : ((SortedSet) c).last();
        }

        public static int rangeResultSize(Object c, boolean isMap) {
                return isMap ? ((SortedMap) c).size() : ((SortedSet) c).size();
        }

        public Block safeFetchFollowers(boolean isMap, String pstateVar, String keyVar, Object startId,
                        Object fanoutLimit, String outFollowerIdsVar, String outNextIdVar) {
                String loopIdVar = Helpers.genVar("loopId");
                String nextLoopIdVar = Helpers.genVar("nextLoopId");
                String subVar = Helpers.genVar("sub");
                SortedRangeFromOptions options = SortedRangeFromOptions.excludeStart().maxAmt(rangeQueryLimit);
                Path.Impl p = Path.key(keyVar);
                p = isMap ? p.sortedMapRangeFrom(loopIdVar, options) : p.sortedSetRangeFrom(loopIdVar, options);

                return Block.each((RamaFunction0) ArrayList::new).out(outFollowerIdsVar)
                                .loopWithVars(LoopVars.var(loopIdVar, startId),
                                                Block.yieldIfOvertime()
                                                                .localSelect(pstateVar, p).out(subVar)
                                                                .each((List l, Object c, Boolean ism) -> {
                                                                        for (Object o : ism ? ((SortedMap) c).values()
                                                                                        : (SortedSet) c)
                                                                                l.add(o);
                                                                        return null;
                                                                }, outFollowerIdsVar, subVar, isMap)
                                                                .each((Object c, Boolean ism,
                                                                                Integer rangeQueryLimit) -> rangeResultSize(
                                                                                                c,
                                                                                                ism) < rangeQueryLimit
                                                                                                                ? null
                                                                                                                : lastItem(c, ism),
                                                                                subVar, isMap, rangeQueryLimit)
                                                                .out(nextLoopIdVar)
                                                                .ifTrue(new Expr(Ops.OR,
                                                                                new Expr(Ops.IS_NULL, nextLoopIdVar),
                                                                                new Expr(Ops.GREATER_THAN_OR_EQUAL,
                                                                                                new Expr(Ops.SIZE,
                                                                                                                outFollowerIdsVar),
                                                                                                fanoutLimit)),
                                                                                Block.emitLoop(nextLoopIdVar),
                                                                                Block.continueLoop(nextLoopIdVar)))
                                .out(outNextIdVar);
        }

        public Block safeFetchMapFollowers(String pstateVar, String keyVar, Object startId, Object fanoutLimit,
                        String outFollowerIdsVar, String outNextIdVar) {
                return safeFetchFollowers(true, pstateVar, keyVar, startId, fanoutLimit, outFollowerIdsVar,
                                outNextIdVar);
        }

        public Block safeFetchSetFollowers(String pstateVar, String keyVar, Object startId, Object fanoutLimit,
                        String outFollowerIdsVar, String outNextIdVar) {
                return safeFetchFollowers(false, pstateVar, keyVar, startId, fanoutLimit, outFollowerIdsVar,
                                outNextIdVar);
        }

        private static boolean needsNewPollVersion(Status prev, Status next) {
                if (prev == null)
                        return false;
                PollContent pcprev = ApolloHelpers.extractPollContent(prev);
                PollContent pcnext = ApolloHelpers.extractPollContent(next);
                if (pcnext == null || pcprev == null)
                        return false;
                else
                        return !pcprev.getChoices().equals(pcnext.getChoices());
        }

        // assumes computation already partitioned by authorIdVar
        public Block conversationFanout(String authorIdVar, String statusVar, String statusIdVar, String contentVar,
                        TaskUniqueIdPState conversationStatusIndex,
                        KeyToLinkedEntitySetPStateGroup accountIdToDirectMessages,
                        KeyToLinkedEntitySetPStateGroup accountIdToConvoIds) {
                String statusPointerVar = Helpers.genVar("statusPointer");
                String convoIdVar = Helpers.genVar("convoId");
                String senderStatusIndexVar = Helpers.genVar("senderStatusIndex");
                String receiverStatusIndexVar = Helpers.genVar("receiverStatusIndex");
                Block ret = Block
                                .each((RamaFunction2<Long, Long, StatusPointer>) StatusPointer::new, authorIdVar,
                                                statusIdVar)
                                .out(statusPointerVar)

                                .macro(extractFields(contentVar, "*text"))
                                .macro(conversationStatusIndex.genId(senderStatusIndexVar))
                                .localSelect("$$statusIdToConvoId", Path.key(statusIdVar).nullToVal(statusIdVar))
                                .out(convoIdVar)

                                .localTransform("$$accountIdToConvoIdToConvo",
                                                Path.key(authorIdVar, convoIdVar, "timeline", senderStatusIndexVar)
                                                                .termVal(statusPointerVar))
                                .localTransform("$$accountIdToConvoIdToConvo",
                                                Path.key(authorIdVar, convoIdVar, "unread").termVal(true))
                                .macro(accountIdToConvoIds.removeFromLinkedSet(authorIdVar, convoIdVar))
                                .macro(accountIdToConvoIds.addToLinkedSet(authorIdVar, convoIdVar))
                                .macro(accountIdToDirectMessages.addToLinkedSet(authorIdVar, statusPointerVar))

                                .each(Token::parseTokens, "*text").out("*tokens")
                                .each(Ops.EXPLODE, "*tokens").out("*token")
                                .each((Token token) -> token.kind, "*token").out("*kind")
                                .ifTrue(new Expr(Ops.EQUAL, Token.TokenKind.MENTION, "*kind"),
                                                Block.each((Token token) -> token.content, "*token").out("*mention")
                                                                .select("$$nameToUser",
                                                                                Path.key("*mention").must("accountId"))
                                                                .out("*accountId")
                                                                .keepTrue(new Expr(Ops.NOT_EQUAL, "*accountId",
                                                                                authorIdVar)) // no need to fan out to
                                                                                              // author since it is
                                                                                              // already saved there
                                                                                              // above
                                                                .hashPartition("*accountId")
                                                                .macro(conversationStatusIndex
                                                                                .genId(receiverStatusIndexVar))
                                                                .localTransform("$$accountIdToConvoIdToConvo", Path
                                                                                .key("*accountId", convoIdVar,
                                                                                                "timeline",
                                                                                                receiverStatusIndexVar)
                                                                                .termVal(statusPointerVar))
                                                                .localTransform("$$accountIdToConvoIdToConvo",
                                                                                Path.key("*accountId", convoIdVar,
                                                                                                "unread").termVal(true))
                                                                .localTransform("$$accountIdToConvoIdToConvo", Path
                                                                                .key("*accountId", convoIdVar,
                                                                                                "accountIds")
                                                                                .voidSetElem().termVal(authorIdVar))
                                                                .macro(accountIdToConvoIds.removeFromLinkedSet(
                                                                                "*accountId", convoIdVar))
                                                                .macro(accountIdToConvoIds.addToLinkedSet("*accountId",
                                                                                convoIdVar))
                                                                .macro(accountIdToDirectMessages.addToLinkedSet(
                                                                                "*accountId", statusPointerVar)));
                return Block.atomicBlock(ret);
        }

        private void declareMicrobatchTopologies(Topologies topologies) {
                MicrobatchTopology fan = topologies.microbatch("fanout");

                // fanout pstates
                fan.pstate("$$statusIdToFollowerFanouts", PState.mapSchema(Long.class, List.class)); // List<FollowerFanout>
                fan.pstate("$$hashtagFanoutToIndex", PState.mapSchema(HashtagFanout.class, Long.class));
                fan.pstate("$$spaceFanoutToIndex", PState.mapSchema(SpaceFanout.class, Long.class));

                // conversations
                TaskUniqueIdPState conversationStatusIndex = new TaskUniqueIdPState("$$conversationStatusIndex")
                                .descending();
                conversationStatusIndex.declarePState(fan);
                KeyToLinkedEntitySetPStateGroup accountIdToConvoIds = new KeyToLinkedEntitySetPStateGroup(
                                "$$accountIdToConvoIds", Long.class, Long.class).descending();
                accountIdToConvoIds.declarePStates(fan);
                fan.pstate("$$accountIdToConvoIdToConvo",
                                PState.mapSchema(Long.class, // account id
                                                PState.mapSchema(Long.class, // conversation id
                                                                PState.fixedKeysSchema("timeline",
                                                                                PState.mapSchema(Long.class, // status
                                                                                                             // index
                                                                                                StatusPointer.class)
                                                                                                .subindexed(),
                                                                                "unread", Boolean.class,
                                                                                "accountIds",
                                                                                PState.setSchema(Long.class)))
                                                                .subindexed()));

                fan.source("*conversationDepot").out("*microbatch")
                                .explodeMicrobatch("*microbatch").out("*data")
                                .subSource("*data",
                                                SubSource.create(EditConversation.class)
                                                                .macro(extractFields("*data", "*accountId",
                                                                                "*conversationId", "*unread"))
                                                                .localTransform("$$accountIdToConvoIdToConvo", Path
                                                                                .key("*accountId", "*conversationId",
                                                                                                "unread")
                                                                                .termVal("*unread")),
                                                SubSource.create(RemoveConversation.class)
                                                                .macro(extractFields("*data", "*accountId",
                                                                                "*conversationId"))
                                                                .localTransform("$$accountIdToConvoIdToConvo",
                                                                                Path.key("*accountId",
                                                                                                "*conversationId")
                                                                                                .termVoid())
                                                                .macro(accountIdToConvoIds.removeFromLinkedSet(
                                                                                "*accountId", "*conversationId")));

                KeyToLinkedEntitySetPStateGroup accountIdToDirectMessages =
                                // stores all DMs in a flat list
                                // necessary for the old (deprecated) DM timeline and for the streaming API --
                                // TODO: Maybe remove
                                new KeyToLinkedEntitySetPStateGroup("$$accountIdToDirectMessages", Long.class,
                                                StatusPointer.class).descending();
                accountIdToDirectMessages.declarePStates(fan);

                fan.source("*statusWithIdDepot").out("*microbatch")
                                .anchor("FanoutRoot")

                                // continue fanout of new statuses to followers
                                .allPartition()
                                .localSelect("$$statusIdToFollowerFanouts", Path.all()).out("*keyAndVal")
                                .each(Ops.EXPAND, "*keyAndVal").out("*statusId", "*followerFanouts")
                                .localTransform("$$statusIdToFollowerFanouts", Path.key("*statusId").termVoid())
                                .each(Ops.EXPLODE, "*followerFanouts").out("*followerFanout")
                                .macro(extractFields("*followerFanout", "*authorId", "*nextIndex", "*fanoutAction",
                                                "*status", "*task"))
                                .each(FanoutAction::getValue, "*fanoutAction").out("*fanoutActionValue")
                                .macro(extractFields("*status", "*content", "*language"))
                                .each((RamaFunction2<Long, Long, StatusPointer>) StatusPointer::new, "*authorId",
                                                "*statusId")
                                .out("*statusPointer")
                                .directPartition("$$partitionedFollowers", "*task")
                                .anchor("FollowerFanoutContinue")

                                // continue fanout of new statuses to hashtags
                                .hook("FanoutRoot")
                                .allPartition()
                                .localSelect("$$hashtagFanoutToIndex", Path.all()).out("*keyAndVal")
                                .each(Ops.EXPAND, "*keyAndVal").out("*hashtagFanout", "*nextIndex")
                                .localTransform("$$hashtagFanoutToIndex", Path.key("*hashtagFanout").termVoid())
                                .macro(extractFields("*hashtagFanout", "*authorId", "*statusId", "*hashtag"))
                                .anchor("HashtagFanoutContinue")

                                // Add new hook chain for spaces
                                .hook("FanoutRoot")
                                .allPartition()
                                .localSelect("$$spaceFanoutToIndex", Path.all()).out("*keyAndVal")
                                .each(Ops.EXPAND, "*keyAndVal").out("*spaceFanout", "*nextIndex")
                                .localTransform("$$spaceFanoutToIndex", Path.key("*spaceFanout").termVoid())
                                .macro(extractFields("*spaceFanout", "*authorId", "*statusId", "*spaceId"))

                                .anchor("SpaceFanoutContinue")

                                // handle incoming depot appends
                                .hook("FanoutRoot")
                                .explodeMicrobatch("*microbatch").out("*data")
                                .subSource("*data",
                                                SubSource.create(EditStatus.class)
                                                                .macro(extractFields("*data", "*statusId", "*status"))
                                                                .macro(extractFields("*status", "*authorId",
                                                                                "*content"))
                                                                .each(Ops.IDENTITY, 0L).out("*nextIndex")
                                                                .each(Ops.IDENTITY, FanoutAction.Edit.getValue())
                                                                .out("*fanoutActionValue"),
                                                SubSource.create(RemoveStatusWithId.class)
                                                                .macro(extractFields("*data", "*statusId", "*status"))
                                                                .macro(extractFields("*status", "*authorId",
                                                                                "*content"))
                                                                .each(Ops.IDENTITY, 0L).out("*nextIndex")
                                                                .each(Ops.IDENTITY, FanoutAction.Remove.getValue())
                                                                .out("*fanoutActionValue"),
                                                SubSource.create(StatusWithId.class)
                                                                .macro(extractFields("*data", "*statusId", "*status"))
                                                                .macro(extractFields("*status", "*authorId", "*content",
                                                                                "*language"))
                                                                // get the visibility
                                                                .each(ApolloHelpers::getStatusVisibility, "*status")
                                                                .out("*visibility")

                                                                // fan out to timelines
                                                                .ifTrue(new Expr(Ops.EQUAL, "*visibility",
                                                                                StatusVisibility.Direct),
                                                                                Block.macro(conversationFanout(
                                                                                                "*authorId", "*status",
                                                                                                "*statusId", "*content",
                                                                                                conversationStatusIndex,
                                                                                                accountIdToDirectMessages,
                                                                                                accountIdToConvoIds)),
                                                                                Block.each(Ops.IDENTITY, -1L)
                                                                                                .out("*nextIndex")
                                                                                                .each(Ops.IDENTITY,
                                                                                                                FanoutAction.Add.getValue())
                                                                                                .out("*fanoutActionValue")
                                                                                                .each((RamaFunction2<Long, Long, StatusPointer>) StatusPointer::new,
                                                                                                                "*authorId",
                                                                                                                "*statusId")
                                                                                                .out("*statusPointer")
                                                                                                .each(HomeTimelines::addTimelineItem,
                                                                                                                "*homeTimelines",
                                                                                                                "*authorId",
                                                                                                                "*statusPointer",
                                                                                                                new Expr(Ops.CURRENT_MICROBATCH_ID))
                                                                                                .anchor("NormalFanoutBegin")
                                                                                                // if status is not a
                                                                                                // boost, parse and fan
                                                                                                // out to the hashtags
                                                                                                .ifTrue(new Expr(Ops.OR,
                                                                                                                new Expr(Ops.IS_INSTANCE_OF,
                                                                                                                                NormalStatusContent.class,
                                                                                                                                "*content"),
                                                                                                                new Expr(Ops.IS_INSTANCE_OF,
                                                                                                                                ReplyStatusContent.class,
                                                                                                                                "*content")),
                                                                                                                Block.keepTrue(new Expr(
                                                                                                                                Ops.NOT_EQUAL,
                                                                                                                                "*visibility",
                                                                                                                                StatusVisibility.Unlisted))
                                                                                                                                .macro(extractFields(
                                                                                                                                                "*content",
                                                                                                                                                "*text"))
                                                                                                                                .each(Token::parseTokens,
                                                                                                                                                "*text")
                                                                                                                                .out("*tokens")
                                                                                                                                .each(Token::filterHashtags,
                                                                                                                                                "*tokens")
                                                                                                                                .out("*hashtags")
                                                                                                                                .each(Ops.EXPLODE,
                                                                                                                                                "*hashtags")
                                                                                                                                .out("*hashtag")
                                                                                                                                .anchor("NormalHashtagFanout")

                                                                                                                                .each(Token::filterSpaces,
                                                                                                                                                "*tokens")
                                                                                                                                .out("*spaceIds")
                                                                                                                                .each(Ops.EXPLODE,
                                                                                                                                                "*spaceIds")
                                                                                                                                .out("*spaceId")

                                                                                                                                .anchor("NormalSpaceFanout"))))

                                .hook("NormalFanoutBegin")
                                .select("$$partitionedFollowersControl", Path.key("*authorId")).out("*tasks")
                                .each(Ops.EXPLODE_INDEXED, "*tasks").out("*i", "*task")
                                // because the first task is always the same as $$partitionedFollowersControl
                                // task for *authorId
                                .ifTrue(new Expr(Ops.NOT_EQUAL, 0, "*i"),
                                                Block.directPartition("$$partitionedFollowers", "*task"))
                                .anchor("NormalFanout")

                                // fanout new status to followers
                                .unify("NormalFanout", "FollowerFanoutContinue")
                                .macro(safeFetchMapFollowers("$$partitionedFollowers", "*authorId", "*nextIndex",
                                                rangeQueryLimit, "*fetchedFollowers", "*nextFollowerId"))
                                // update fanout pstate if necessary
                                .ifTrue(new Expr(Ops.IS_NOT_NULL, "*nextFollowerId"),
                                                Block.each((RamaFunction5<Long, Long, FanoutAction, Status, Integer, FollowerFanout>) FollowerFanout::new,
                                                                "*authorId", "*nextFollowerId",
                                                                new Expr(FanoutAction::findByValue,
                                                                                "*fanoutActionValue"),
                                                                "*status", "*task").out("*followerFanout")
                                                                .localTransform("$$statusIdToFollowerFanouts", Path
                                                                                .key("*statusId").nullToList()
                                                                                .afterElem()
                                                                                .termVal("*followerFanout")))
                                .each(Ops.EXPLODE, "*fetchedFollowers").out("*follower")
                                .each((Follower follower) -> follower.accountId, "*follower").out("*followerId")
                                .each((Follower follower) -> follower.isShowBoosts(), "*follower").out("*showBoosts")
                                .each((Follower follower) -> follower.getLanguages(), "*follower").out("*languages")
                                // stop if it's a boost of the recipient or of someone for whom the recipient
                                // disabled boosts
                                .ifTrue(new Expr(Ops.IS_INSTANCE_OF, BoostStatusContent.class, "*content"),
                                                Block.macro(extractFields("*content", "*boosted"))
                                                                .each((StatusPointer boosted) -> boosted.authorId,
                                                                                "*boosted")
                                                                .out("*boostedAuthorId")
                                                                .keepTrue(new Expr(Ops.NOT_EQUAL, "*boostedAuthorId",
                                                                                "*followerId"))
                                                                .keepTrue("*showBoosts"))
                                // stop if language is set on both status and follower and the language doesn't
                                // match
                                .keepTrue(new Expr((List<String> languages, String statusLanguage) -> languages == null
                                                || statusLanguage == null || languages.contains(statusLanguage),
                                                "*languages", "*language"))
                                // stop if it's a reply and the recipient isn't following the parent
                                .ifTrue(new Expr(Ops.IS_INSTANCE_OF, ReplyStatusContent.class, "*content"),
                                                Block.macro(extractFields("*content", "*parent"))
                                                                .each((StatusPointer parent) -> parent.authorId,
                                                                                "*parent")
                                                                .out("*parentAuthorId")
                                                                .hashPartition("*followerId")
                                                                .macro(fetchBloomMacro("*followerId", "*rbloom"))
                                                                .keepTrue(new Expr((RBloomFilter rbloom,
                                                                                Long accountId) -> rbloom.bloom
                                                                                                .isPresent("" + accountId),
                                                                                "*rbloom", "*parentAuthorId"))
                                                                .select("$$followerToFollowees",
                                                                                Path.key("*followerId").must(
                                                                                                "*parentAuthorId")))
                                .hashPartition("*followerId")
                                .each(HomeTimelines::addTimelineItem, "*homeTimelines", "*followerId", "*statusPointer",
                                                new Expr(Ops.CURRENT_MICROBATCH_ID))

                                // fan out new status to hashtags
                                .unify("NormalHashtagFanout", "HashtagFanoutContinue")
                                .each((RamaFunction2<Long, Long, StatusPointer>) StatusPointer::new, "*authorId",
                                                "*statusId")
                                .out("*statusPointer")
                                .hashPartition("$$hashtagToFollowers", "*hashtag")
                                .macro(safeFetchSetFollowers("$$hashtagToFollowers", "*hashtag", "*nextIndex",
                                                singlePartitionFanoutLimit, "*fetchedFollowerIds", "*nextFollowerId"))
                                // update fanout pstate if necessary
                                .ifTrue(new Expr(Ops.IS_NOT_NULL, "*nextFollowerId"),
                                                Block.each((RamaFunction3<Long, Long, String, HashtagFanout>) HashtagFanout::new,
                                                                "*authorId", "*statusId", "*hashtag")
                                                                .out("*followerFanout")
                                                                .localTransform("$$hashtagFanoutToIndex",
                                                                                Path.key("*followerFanout").termVal(
                                                                                                "*nextFollowerId")))
                                .each(Ops.EXPLODE, "*fetchedFollowerIds").out("*followerId")
                                .hashPartition("*followerId")
                                .each(HomeTimelines::addTimelineItem, "*homeTimelines", "*followerId", "*statusPointer",
                                                new Expr(Ops.CURRENT_MICROBATCH_ID))

                                // fan out new status to spaces
                                .unify("NormalSpaceFanout", "SpaceFanoutContinue")
                                .each((RamaFunction2<Long, Long, StatusPointer>) StatusPointer::new, "*authorId",
                                                "*statusId")
                                .out("*statusPointer")
                                .hashPartition("$$spaceIdToFollowers", "*spaceId")
                                .macro(safeFetchSetFollowers("$$spaceIdToFollowers", "*spaceId", "*nextIndex",
                                                singlePartitionFanoutLimit, "*fetchedFollowerIds", "*nextFollowerId"))
                                // update fanout pstate if necessary
                                .ifTrue(new Expr(Ops.IS_NOT_NULL, "*nextFollowerId"),
                                                Block.each((RamaFunction3<Long, Long, String, SpaceFanout>) SpaceFanout::new,
                                                                "*authorId", "*statusId", "*spaceId")
                                                                .out("*followerFanout")
                                                                .localTransform("$$spaceFanoutToIndex",
                                                                                Path.key("*followerFanout").termVal(
                                                                                                "*nextFollowerId")))
                                .each(Ops.EXPLODE, "*fetchedFollowerIds").out("*followerId")
                                .hashPartition("*followerId")
                                .each(HomeTimelines::addTimelineItem, "*homeTimelines", "*followerId", "*statusPointer",
                                                new Expr(Ops.CURRENT_MICROBATCH_ID));

                // This topology handles other status-related features that are ok with latency
                // in the 300ms range
                MicrobatchTopology core = topologies.microbatch("core");

                KeyToLinkedEntitySetPStateGroup likerToStatusPointers = new KeyToLinkedEntitySetPStateGroup(
                                "$$likerToStatusPointers", Long.class, StatusPointer.class).descending();
                KeyToLinkedEntitySetPStateGroup statusIdToLikers = new KeyToLinkedEntitySetPStateGroup(
                                "$$statusIdToLikers", Long.class, Long.class).descending();
                likerToStatusPointers.declarePStates(core);
                statusIdToLikers.declarePStates(core);

                core.source("*likeStatusDepot").out("*mb")
                                .explodeMicrobatch("*mb").out("*data")
                                .subSource("*data",
                                                SubSource.create(LikeStatus.class)
                                                                .macro(extractFields("*data", "*accountId", "*target"))
                                                                .macro(extractFields("*target", "*authorId",
                                                                                "*statusId"))
                                                                .macro(likerToStatusPointers.addToLinkedSet(
                                                                                "*accountId", "*target"))
                                                                .hashPartition("*authorId")
                                                                .macro(statusIdToLikers.addToLinkedSet("*statusId",
                                                                                "*accountId")),
                                                SubSource.create(RemoveLikeStatus.class)
                                                                .macro(extractFields("*data", "*accountId", "*target"))
                                                                .macro(extractFields("*target", "*authorId",
                                                                                "*statusId"))
                                                                .macro(likerToStatusPointers.removeFromLinkedSet(
                                                                                "*accountId", "*target"))
                                                                .hashPartition("*authorId")
                                                                .macro(statusIdToLikers.removeFromLinkedSet("*statusId",
                                                                                "*accountId")));

                KeyToLinkedEntitySetPStateGroup bookmarkerToStatusPointers = new KeyToLinkedEntitySetPStateGroup(
                                "$$bookmarkerToStatusPointers", Long.class, StatusPointer.class).descending();
                bookmarkerToStatusPointers.declarePStates(core);
                core.pstate("$$statusIdToBookmarkers",
                                PState.mapSchema(Long.class, PState.setSchema(Long.class).subindexed()));
                core.source("*bookmarkStatusDepot").out("*mb")
                                .explodeMicrobatch("*mb").out("*data")
                                .subSource("*data",
                                                SubSource.create(BookmarkStatus.class)
                                                                .macro(extractFields("*data", "*accountId", "*target"))
                                                                .macro(extractFields("*target", "*authorId",
                                                                                "*statusId"))
                                                                .macro(bookmarkerToStatusPointers.addToLinkedSet(
                                                                                "*accountId", "*target"))
                                                                .hashPartition("*authorId")
                                                                .localTransform("$$statusIdToBookmarkers",
                                                                                Path.key("*statusId").voidSetElem()
                                                                                                .termVal("*accountId")),
                                                SubSource.create(RemoveBookmarkStatus.class)
                                                                .macro(extractFields("*data", "*accountId", "*target"))
                                                                .macro(extractFields("*target", "*authorId",
                                                                                "*statusId"))
                                                                .macro(bookmarkerToStatusPointers.removeFromLinkedSet(
                                                                                "*accountId", "*target"))
                                                                .hashPartition("*authorId")
                                                                .localTransform("$$statusIdToBookmarkers",
                                                                                Path.key("*statusId")
                                                                                                .setElem("*accountId")
                                                                                                .termVoid()));

                core.pstate("$$muterToStatusIds",
                                PState.mapSchema(Long.class, PState.setSchema(Long.class).subindexed()));
                core.pstate("$$statusIdToMuters",
                                PState.mapSchema(Long.class, PState.setSchema(Long.class).subindexed()));
                core.source("*muteStatusDepot").out("*mb")
                                .explodeMicrobatch("*mb").out("*data")
                                .subSource("*data",
                                                SubSource.create(MuteStatus.class)
                                                                .macro(extractFields("*data", "*accountId", "*target"))
                                                                .macro(extractFields("*target", "*authorId",
                                                                                "*statusId"))
                                                                .localTransform("$$muterToStatusIds",
                                                                                Path.key("*accountId").voidSetElem()
                                                                                                .termVal("*statusId"))
                                                                .hashPartition("*authorId")
                                                                .localTransform("$$statusIdToMuters",
                                                                                Path.key("*statusId").voidSetElem()
                                                                                                .termVal("*accountId")),
                                                SubSource.create(RemoveMuteStatus.class)
                                                                .macro(extractFields("*data", "*accountId", "*target"))
                                                                .macro(extractFields("*target", "*authorId",
                                                                                "*statusId"))
                                                                .localTransform("$$muterToStatusIds",
                                                                                Path.key("*accountId")
                                                                                                .setElem("*statusId")
                                                                                                .termVoid())
                                                                .hashPartition("*authorId")
                                                                .localTransform("$$statusIdToMuters",
                                                                                Path.key("*statusId")
                                                                                                .setElem("*accountId")
                                                                                                .termVoid()));

                KeyToUniqueFixedItemsPStateGroup pinnerToStatusIds = new KeyToUniqueFixedItemsPStateGroup(
                                "$$pinnerToStatusIds", PINNED_STATUS_MAX_AMOUNT, Long.class, Long.class);
                pinnerToStatusIds.declarePStates(core);
                core.source("*pinStatusDepot").out("*mb")
                                .explodeMicrobatch("*mb").out("*data")
                                .subSource("*data",
                                                SubSource.create(PinStatus.class)
                                                                .macro(extractFields("*data", "*accountId",
                                                                                "*statusId"))
                                                                .macro(pinnerToStatusIds.addItem("*accountId",
                                                                                "*statusId")),
                                                SubSource.create(RemovePinStatus.class)
                                                                .macro(extractFields("*data", "*accountId",
                                                                                "*statusId"))
                                                                .macro(pinnerToStatusIds.removeItem("*accountId",
                                                                                "*statusId")));
        }

        private static Block removeStatusMacro(String accountIdVar, String statusIdVar) {
                String statusVar = Helpers.genVar("status");
                String removeStatusWithIdVar = Helpers.genVar("removeStatusWithId");
                return Block.select("$$accountIdToStatuses", Path.key(accountIdVar, statusIdVar).view(Ops.FIRST))
                                .out(statusVar)
                                .localTransform("$$accountIdToStatuses", Path.key(accountIdVar, statusIdVar).termVoid())
                                .localTransform("$$accountIdToAccountTimeline",
                                                Path.key(accountIdVar).setElem(statusIdVar).termVoid())
                                .localTransform("$$accountIdToAttachmentStatusIds",
                                                Path.key(accountIdVar).setElem(statusIdVar).termVoid())
                                .ifTrue(new Expr(Ops.IS_NOT_NULL, statusVar),
                                                Block.each((Long statusId, Status status) -> new RemoveStatusWithId(
                                                                statusId, status), statusIdVar, statusVar)
                                                                .out(removeStatusWithIdVar)
                                                                .depotPartitionAppend("*statusWithIdDepot",
                                                                                removeStatusWithIdVar));
        }

        private static Block removeAccountMacro(String accountIdVar) {
                String accountVar = Helpers.genVar("account");
                String removeAccountWithIdVar = Helpers.genVar("removeAccountWithId");
                return Block.select("$$accountIdToAccount", Path.key(accountIdVar))
                                .out(accountVar)
                                .macro(extractFields(accountVar, "*email", "*name"))
                                .hashPartition(accountIdVar)
                                .localTransform("$$accountIdToAccount", Path.key(accountIdVar).termVoid())
                                .hashPartition("*email")
                                .localTransform("$$emailToAccountId", Path.key("*email").termVoid())
                                .hashPartition("*name")
                                .localTransform("$$nameToUser", Path.key("*name").termVoid())
                                .each((Long accountId, Account account) -> new RemoveAccountWithId(
                                                accountId, account), accountIdVar, accountVar)
                                .out(removeAccountWithIdVar)
                                .depotPartitionAppend("*accountWithIdDepot",
                                                removeAccountWithIdVar);

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
                stream.pstate("$$emailToAccountId", PState.mapSchema(String.class, Long.class));
                stream.pstate("$$accountIdToAccount", PState.mapSchema(Long.class, Account.class));

                stream.source("*accountDepot").out("*data")
                                .subSource("*data",
                                                SubSource.create(Account.class)
                                                                .macro(extractFields("*data", "*name", "*email",
                                                                                "*uuid"))
                                                                .localSelect("$$nameToUser", Path.key("*name"))
                                                                .out("*currInfo")
                                                                .each(Ops.GET, "*currInfo", "uuid").out("*currUUID")
                                                                // By including a UUID with each registration request,
                                                                // we can distinguish
                                                                // between:
                                                                // - this name is already registered by a different
                                                                // request so we shouldn't
                                                                // override it
                                                                // - this name was registered by the same request, so we
                                                                // should continue
                                                                // finishing the
                                                                // registration
                                                                .ifTrue(new Expr(Ops.OR,
                                                                                new Expr(Ops.IS_NULL, "*currInfo"),
                                                                                new Expr(Ops.EQUAL, "*uuid",
                                                                                                "*currUUID")),
                                                                                Block.macro(accountIdGen
                                                                                                .genId("*accountId"))
                                                                                                .localTransform("$$nameToUser",
                                                                                                                Path.key("*name")
                                                                                                                                .multiPath(Path
                                                                                                                                                .key("accountId")
                                                                                                                                                .termVal("*accountId"),
                                                                                                                                                Path.key("uuid").termVal(
                                                                                                                                                                "*uuid")))
                                                                                                .hashPartition("*accountId")
                                                                                                .localTransform("$$accountIdToAccount",
                                                                                                                Path.key("*accountId")
                                                                                                                                .termVal("*data"))
                                                                                                .invokeQuery("getAccountMetadata",
                                                                                                                null,
                                                                                                                "*accountId")
                                                                                                .out("*metadata")
                                                                                                .each((RamaFunction3<Long, Account, AccountMetadata, AccountWithId>) AccountWithId::new,
                                                                                                                "*accountId",
                                                                                                                "*data",
                                                                                                                "*metadata")
                                                                                                .out("*accountWithId")
                                                                                                .localTransform("$$emailToAccountId",
                                                                                                                Path.key("*email")
                                                                                                                                .termVal("*accountId"))
                                                                                                .depotPartitionAppend(
                                                                                                                "*accountWithIdDepot",
                                                                                                                "*accountWithId")),

                                                SubSource.create(RemoveAccount.class)
                                                                .macro(extractFields("*data", "*accountId"))
                                                                .macro(removeAccountMacro("*accountId")));

                stream.source("*accountEditDepot", StreamSourceOptions.retryNone()).out("*editAccount")
                                .macro(extractFields("*editAccount", "*accountId", "*edits"))
                                .each(Ops.EXPLODE, "*edits").out("*edit")
                                .each((EditAccountField editAccount, OutputCollector collector) -> {
                                        collector.emit(editAccount.getSetField().getFieldName(),
                                                        editAccount.getFieldValue());
                                }, "*edit").out("*fieldName", "*fieldValue")
                                .localTransform("$$accountIdToAccount", Path.must("*accountId")
                                                .customNavBuilder(TField::new, "*fieldName")
                                                .termVal("*fieldValue"));

        }

        // Defines the topology for handling status updates and interactions.
        private void declareStatusTopology(Topologies topologies) {
                // Define a stream for processing status updates.
                StreamTopology stream = topologies.stream("status");

                // Generate unique status IDs in descending order and declare this PState in the
                // stream.
                ModuleUniqueIdPState statusIdGen = new ModuleUniqueIdPState("$$statusIdGen").descending();
                statusIdGen.declarePState(stream);

                // Declare PStates for account statuses, account timelines, post UUIDs to status
                // IDs mapping
                // These PStates use various schema types to model the relationships between
                // accounts, statuses, and external references.
                stream.pstate("$$accountIdToStatuses", PState.mapSchema(Long.class, // account id
                                PState.mapSchema(Long.class, // status id
                                                PState.listSchema(Status.class)).subindexed()));
                stream.pstate("$$accountIdToAccountTimeline",
                                PState.mapSchema(Long.class, PState.setSchema(Long.class).subindexed()));

                stream.pstate("$$postUUIDToStatusId", PState.mapSchema(String.class, Long.class));

                // Handle status replies and boosts by associating them with their parent
                // statuses.
                // This is done through KeyToLinkedEntitySetPStateGroup, which manages
                // relationships between entities.
                KeyToLinkedEntitySetPStateGroup statusIdToReplies = new KeyToLinkedEntitySetPStateGroup(
                                "$$statusIdToReplies",
                                Long.class, StatusPointer.class);
                statusIdToReplies.declarePStates(stream);

                stream.pstate("$$accountIdToAttachmentStatusIds",
                                PState.mapSchema(Long.class, PState.setSchema(Long.class).subindexed()));
                stream.pstate("$$uuidToAttachment", PState.mapSchema(String.class, Attachment.class));

                KeyToLinkedEntitySetPStateGroup statusIdToBoosters = new KeyToLinkedEntitySetPStateGroup(
                                "$$statusIdToBoosters",
                                Long.class, StatusPointer.class)
                                .entityIdFunction(Long.class, (Object p) -> ((StatusPointer) p).authorId)
                                .descending();
                statusIdToBoosters.declarePStates(stream);

                stream.pstate("$$statusIdToConvoId", PState.mapSchema(Long.class, Long.class));

                // Schedule statuses for future posting
                TopologyScheduler scheduledStatuses = new TopologyScheduler("$$scheduledStatuses");
                scheduledStatuses.declarePStates(stream);

                stream.pstate("$$accountIdToScheduledStatuses", PState.mapSchema(Long.class, // accountId
                                PState.mapSchema(Long.class, // id
                                                PState.fixedKeysSchema("publishMillis", Long.class,
                                                                "uuid", String.class,
                                                                "status", Status.class))
                                                .subindexed()));

                stream.pstate("$$pollVotes",
                                PState.mapSchema(
                                                Long.class, // statusId
                                                PState.fixedKeysSchema("allVoters",
                                                                PState.mapSchema(Long.class, Set.class).subindexed(), // accountId
                                                                                                                      // ->
                                                                                                                      // choicesIndexes
                                                                "choices",
                                                                PState.mapSchema(Integer.class, PState
                                                                                .setSchema(Long.class).subindexed())))); // choiceIndex
                                                                                                                         // ->
                                                                                                                         // set
                                                                                                                         // of
                                                                                                                         // accountId

                // Process incoming data (e.g., initial data, status depot updates, and
                // scheduled statuses) through a series of operations.
                // These operations include filtering, transformation, aggregation, and
                // conditional logic to handle different types of status updates and their
                // effects on the system state.
                stream.source("*statusDepot").out("*initialData")
                                // Check if the initial data is a boosted status; if so, create a status from
                                // the boost,
                                // otherwise pass the data through unchanged.
                                .ifTrue(new Expr(Ops.IS_INSTANCE_OF, BoostStatus.class, "*initialData"),
                                                Block.each(ApolloHelpers::createAddStatusFromBoost, "*initialData")
                                                                .out("*data"),
                                                Block.each(Ops.IDENTITY, "*initialData").out("*data"))
                                // Process the data further based on its type.
                                .subSource("*data",
                                                SubSource.create(AddStatus.class)
                                                                // Extract and map necessary fields from the status
                                                                // data.
                                                                .macro(extractFields("*data", "*uuid", "*status"))
                                                                .macro(extractFields("*status", "*authorId",
                                                                                "*content"))

                                                                // Attempt to select an existing status ID by UUID; if
                                                                // it exists, use it,
                                                                // otherwise generate a new one.
                                                                .localSelect("$$postUUIDToStatusId", Path.key("*uuid"))
                                                                .out("*statusIdMaybe")
                                                                .ifTrue(new Expr(Ops.IS_NOT_NULL, "*statusIdMaybe"),
                                                                                Block.each(Ops.IDENTITY,
                                                                                                "*statusIdMaybe")
                                                                                                .out("*statusId"),
                                                                                // otherwise, generate status id and
                                                                                // associate it with the uuid
                                                                                Block.macro(statusIdGen
                                                                                                .genId("*statusId"))
                                                                                                .localTransform("$$postUUIDToStatusId",
                                                                                                                Path.key("*uuid")
                                                                                                                                .termVal("*statusId")))
                                                                // Transform the account's statuses by appending the
                                                                // current status under the
                                                                // generated or selected ID.
                                                                .localTransform("$$accountIdToStatuses",
                                                                                Path.key("*authorId", "*statusId")
                                                                                                .beforeElem()
                                                                                                .termVal("*status"))
                                                                // Special handling for reply content, including setting
                                                                // conversation IDs and
                                                                // updating relevant PStates.
                                                                .ifTrue(new Expr(Ops.IS_INSTANCE_OF,
                                                                                ReplyStatusContent.class, "*content"),
                                                                                Block.macro(ApolloHelpers.extractFields(
                                                                                                "*content", "*parent"))
                                                                                                .each((StatusPointer parent) -> parent.authorId,
                                                                                                                "*parent")
                                                                                                .out("*parentAuthorId")
                                                                                                .each((StatusPointer parent) -> parent.statusId,
                                                                                                                "*parent")
                                                                                                .out("*parentStatusId")
                                                                                                .hashPartition("*parentAuthorId")
                                                                                                .localSelect("$$statusIdToConvoId",
                                                                                                                Path.key("*parentStatusId")
                                                                                                                                .nullToVal("*parentStatusId"))
                                                                                                .out("*convoId")
                                                                                                .hashPartition("*authorId")
                                                                                                .localTransform("$$statusIdToConvoId",
                                                                                                                Path.key("*statusId")
                                                                                                                                .termVal("*convoId")))
                                                                // Ensure updates to $$accountIdToStatuses and
                                                                // $$statusIdToConvoId occur before
                                                                // fanout happens.
                                                                .hashPartition("*authorId")
                                                                // Wrap the status in a StatusWithId object before
                                                                // appending it to a depot.
                                                                .each((RamaFunction2<Long, Status, StatusWithId>) StatusWithId::new,
                                                                                "*statusId",
                                                                                "*status")
                                                                .out("*statusWithId")
                                                                .depotPartitionAppend("*statusWithIdDepot",
                                                                                "*statusWithId")

                                                                // Exclude direct messages from the account timeline.
                                                                .ifTrue(new Expr(Ops.NOT_EQUAL, new Expr(
                                                                                ApolloHelpers::getStatusVisibility,
                                                                                "*status"),
                                                                                StatusVisibility.Direct),
                                                                                Block.localTransform(
                                                                                                "$$accountIdToAccountTimeline",
                                                                                                Path.key("*authorId")
                                                                                                                .voidSetElem()
                                                                                                                .termVal("*statusId")))
                                                                // Process attachments, adding status ID to a set for
                                                                // query purposes if
                                                                // attachments are present.
                                                                .ifTrue(new Expr(Ops.OR,
                                                                                new Expr(Ops.IS_INSTANCE_OF,
                                                                                                NormalStatusContent.class,
                                                                                                "*content"),
                                                                                new Expr(Ops.IS_INSTANCE_OF,
                                                                                                ReplyStatusContent.class,
                                                                                                "*content")),
                                                                                Block.macro(extractFields("*content",
                                                                                                "*attachments"))
                                                                                                .ifTrue(new Expr(
                                                                                                                Ops.AND,
                                                                                                                new Expr(Ops.IS_NOT_NULL,
                                                                                                                                "*attachments"),
                                                                                                                new Expr(Ops.GREATER_THAN,
                                                                                                                                new Expr(Ops.SIZE,
                                                                                                                                                "*attachments"),
                                                                                                                                0)),
                                                                                                                // Add
                                                                                                                // status
                                                                                                                // ID to
                                                                                                                // a set
                                                                                                                // to
                                                                                                                // enable
                                                                                                                // querying
                                                                                                                // all
                                                                                                                // statuses
                                                                                                                // with
                                                                                                                // attachments.
                                                                                                                Block.localTransform(
                                                                                                                                "$$accountIdToAttachmentStatusIds",
                                                                                                                                Path.key("*authorId")
                                                                                                                                                .voidSetElem()
                                                                                                                                                .termVal("*statusId"))))
                                                                // Process reply statuses by creating a link between the
                                                                // reply and its parent
                                                                // status.
                                                                .ifTrue(new Expr(Ops.IS_INSTANCE_OF,
                                                                                ReplyStatusContent.class, "*content"),
                                                                                Block.each((RamaFunction2<Long, Long, StatusPointer>) StatusPointer::new,
                                                                                                "*authorId",
                                                                                                "*statusId")
                                                                                                .out("*statusPointer")
                                                                                                .macro(ApolloHelpers
                                                                                                                .extractFields("*content",
                                                                                                                                "*parent"))
                                                                                                .each((StatusPointer parent) -> parent.authorId,
                                                                                                                "*parent")
                                                                                                .out("*parentAuthorId")
                                                                                                .each((StatusPointer parent) -> parent.statusId,
                                                                                                                "*parent")
                                                                                                .out("*parentStatusId")
                                                                                                .hashPartition("*parentAuthorId")
                                                                                                // Add the reply status
                                                                                                // to a linked set
                                                                                                // organized by parent
                                                                                                // status ID.
                                                                                                .macro(statusIdToReplies
                                                                                                                .addToLinkedSet("*parentStatusId",
                                                                                                                                "*statusPointer")))
                                                                // Handle boosted statuses by linking boosters to the
                                                                // original status.
                                                                .ifTrue(new Expr(Ops.IS_INSTANCE_OF,
                                                                                BoostStatusContent.class, "*content"),
                                                                                Block.macro(extractFields("*content",
                                                                                                "*boosted"))
                                                                                                .each((StatusPointer boosted) -> boosted.authorId,
                                                                                                                "*boosted")
                                                                                                .out("*boostedAuthorId")
                                                                                                .each((StatusPointer boosted) -> boosted.statusId,
                                                                                                                "*boosted")
                                                                                                .out("*boostedStatusId")
                                                                                                .hashPartition("*boostedAuthorId")
                                                                                                .each((RamaFunction2<Long, Long, StatusPointer>) StatusPointer::new,
                                                                                                                "*authorId",
                                                                                                                "*statusId")
                                                                                                .out("*sp")
                                                                                                // Add the booster to a
                                                                                                // linked set organized
                                                                                                // by the original
                                                                                                // status ID.
                                                                                                .macro(statusIdToBoosters
                                                                                                                .addToLinkedSet("*boostedStatusId",
                                                                                                                                "*sp"))),

                                                // Begin processing for editing a status post, targeting the
                                                // 'EditStatus' type
                                                // of data. TODO: Possibly remove this functionality or make it for
                                                // verified
                                                // users
                                                SubSource.create(EditStatus.class)
                                                                // Extract 'statusId' and 'status' fields from the data
                                                                // to be processed.
                                                                .macro(extractFields("*data", "*statusId", "*status"))
                                                                // Extract 'authorId' from the status to identify the
                                                                // post's owner.
                                                                .macro(extractFields("*status", "*authorId"))
                                                                // Attempt to select the previous version of the status
                                                                // post using the authorId
                                                                // and statusId.
                                                                // This operation also applies a limit to the number of
                                                                // edits allowed
                                                                // (maxEditCount).
                                                                .localSelect("$$accountIdToStatuses", Path
                                                                                .key("*authorId", "*statusId")
                                                                                .view((List l, Integer max) -> {
                                                                                        // If there are too many edits
                                                                                        // or none, return null;
                                                                                        // otherwise, return the
                                                                                        // first post.
                                                                                        if (l == null || l
                                                                                                        .size() >= max)
                                                                                                return null;
                                                                                        else
                                                                                                return l.get(0);
                                                                                }, maxEditCount))
                                                                .out("*prevStatus")
                                                                // Update the PState for account statuses by inserting
                                                                // the new status content,
                                                                // ensuring it does not exceed the maximum allowed
                                                                // edits.
                                                                .localTransform("$$accountIdToStatuses", Path
                                                                                .key("*authorId").must("*statusId")
                                                                                .filterSelected(Path.view(Ops.SIZE)
                                                                                                .filterLessThan(maxEditCount))
                                                                                .beforeElem()
                                                                                .termVal("*status"))
                                                                // Check if the status edit involves changes to a poll.
                                                                // If so, and if a new poll
                                                                // version is required, clear existing votes.
                                                                .ifTrue(new Expr(Core::needsNewPollVersion,
                                                                                "*prevStatus", "*status"),
                                                                                Block.localTransform("$$pollVotes", Path
                                                                                                .key("*statusId")
                                                                                                .termVal(null)))
                                                                // Append the edited status data to a dedicated depot
                                                                // for processed status
                                                                // updates.
                                                                .depotPartitionAppend("*statusWithIdDepot", "*data"),

                                                // Define a sub-processing flow for removing a status post.
                                                SubSource.create(RemoveStatus.class)
                                                                // Extract necessary identifiers from the data:
                                                                // accountId and statusId.
                                                                .macro(extractFields("*data", "*accountId",
                                                                                "*statusId"))
                                                                // Invoke a macro to handle the removal logic for the
                                                                // specified status post.
                                                                .macro(removeStatusMacro("*accountId", "*statusId")),

                                                // Define a sub-processing flow for removing a boosted status post.
                                                SubSource.create(RemoveBoostStatus.class)
                                                                // Extract accountId and target (the original status
                                                                // being boosted) from the
                                                                // data.
                                                                .macro(extractFields("*data", "*accountId", "*target"))
                                                                // Further extract the authorId and statusId of the
                                                                // original status from the
                                                                // target.
                                                                .macro(extractFields("*target", "*authorId",
                                                                                "*statusId"))
                                                                // Partition the stream by the authorId of the original
                                                                // status to ensure
                                                                // consistency in distributed processing.
                                                                .hashPartition("*authorId")
                                                                // Select the identifier for the boost relationship
                                                                // based on the statusId and
                                                                // accountId.
                                                                // This operation ensures that only the correct boost
                                                                // relationship is targeted
                                                                // for removal.
                                                                .localSelect("$$statusIdToBoosters",
                                                                                Path.key("*statusId")
                                                                                                .must("*accountId"))
                                                                .out("*id")
                                                                // Select the specific boost relationship using the
                                                                // previously obtained
                                                                // identifier.
                                                                .localSelect("$$statusIdToBoostersById",
                                                                                Path.key("*statusId", "*id"))
                                                                .out("*sp")
                                                                // Extract the boostStatusId from the selected boost
                                                                // relationship.
                                                                .each((StatusPointer sp) -> sp.statusId, "*sp")
                                                                .out("*boostStatusId")
                                                                // Invoke a macro to handle the removal logic for the
                                                                // boosted status post.
                                                                .macro(removeStatusMacro("*accountId",
                                                                                "*boostStatusId"))
                                                                // Re-partition the stream by authorId to maintain
                                                                // consistency.
                                                                .hashPartition("*authorId")
                                                                // Remove the selected boost relationship from the
                                                                // linked set of boosters by
                                                                // entityId.
                                                                .macro(statusIdToBoosters.removeFromLinkedSetByEntityId(
                                                                                "*statusId", "*accountId")));

                // Start processing incoming data from the "statusAttachmentWithIdDepot".
                stream.source("*statusAttachmentWithIdDepot").out("*data")
                                .subSource("*data",
                                                // Focus on data of type AttachmentWithId for processing.
                                                SubSource.create(AttachmentWithId.class)
                                                                // Extract 'uuid' (unique identifier for the attachment)
                                                                // and the 'attachment'
                                                                // itself.
                                                                .macro(extractFields("*data", "*uuid", "*attachment"))
                                                                // Update the PState mapping UUIDs to attachments,
                                                                // effectively associating the
                                                                // attachment with its UUID.
                                                                .localTransform("$$uuidToAttachment", Path.key("*uuid")
                                                                                .termVal("*attachment")));

                // Begin processing poll vote data coming from "pollVoteDepot".
                stream.source("*pollVoteDepot").out("*data")
                                // Extract accountId, target (the poll's target status), and choices (the
                                // selected poll options).
                                .macro(extractFields("*data", "*accountId", "*target", "*choices"))
                                // Further extract the statusId from the target, linking the vote to a specific
                                // status post.
                                .macro(extractFields("*target", "*statusId"))
                                // Transform the $$pollVotes PState with detailed path selections for votes.
                                .localTransform("$$pollVotes", Path.key("*statusId")
                                                // Define multiple paths for updating votes:
                                                // - Path for "allVoters" maps accountId to their choices.
                                                // - Path for "choices" updates the count of votes for each choice.
                                                .multiPath(Path.key("allVoters").key("*accountId").termVal("*choices"),
                                                                Path.key("choices")
                                                                                // Dynamic path building based on the
                                                                                // set of choices. For each choice in
                                                                                // the
                                                                                // set,
                                                                                // a path is created, and the accountId
                                                                                // is added to the set of voters for
                                                                                // that
                                                                                // choice.
                                                                                .pathBuilder((Set choices) -> {
                                                                                        Path.Impl ret = Path.stop();
                                                                                        for (Object c : choices)
                                                                                                ret = Path.multiPath(
                                                                                                                ret,
                                                                                                                Path.key(c));
                                                                                        return ret;
                                                                                }, "*choices")
                                                                                .voidSetElem()
                                                                                .termVal("*accountId")));

                // Begin processing data from the "scheduledStatusDepot".
                stream.source("*scheduledStatusDepot").out("*data")
                                .subSource("*data",
                                                // Process adding new scheduled statuses.
                                                SubSource.create(AddScheduledStatus.class)
                                                                // Extract essential fields: uuid, status content, and
                                                                // scheduled publish time.
                                                                .macro(extractFields("*data", "*uuid", "*status",
                                                                                "*publishMillis"))
                                                                .macro(extractFields("*status", "*authorId"))
                                                                // Attempt to select an existing status ID by UUID; if
                                                                // not found, generate a new
                                                                // one.
                                                                .localSelect("$$postUUIDToStatusId", Path.key("*uuid"))
                                                                .out("*statusIdMaybe")
                                                                .ifTrue(new Expr(Ops.IS_NOT_NULL, "*statusIdMaybe"),
                                                                                Block.each(Ops.IDENTITY,
                                                                                                "*statusIdMaybe")
                                                                                                .out("*id"),
                                                                                Block.macro(statusIdGen.genId("*id"))
                                                                                                .localTransform("$$postUUIDToStatusId",
                                                                                                                Path.key("*uuid")
                                                                                                                                .termVal("*id")))
                                                                // Transform the PState for scheduled statuses with new
                                                                // or updated information.
                                                                .localTransform("$$accountIdToScheduledStatuses", Path
                                                                                .key("*authorId", "*id")
                                                                                .multiPath(Path.key("status")
                                                                                                .termVal("*status"),
                                                                                                Path.key("uuid").termVal(
                                                                                                                "*uuid"),
                                                                                                Path.key("publishMillis")
                                                                                                                .termVal("*publishMillis")))
                                                                // Schedule the status post for future publication.
                                                                .macro(scheduledStatuses.scheduleItem("*publishMillis",
                                                                                new Expr(Ops.TUPLE, "*authorId", "*id",
                                                                                                "*uuid"))),

                                                // Process editing an existing status.
                                                SubSource.create(EditStatus.class)
                                                                .macro(extractFields("*data", "*statusId", "*status"))
                                                                .macro(extractFields("*status", "*authorId"))
                                                                // Update the scheduled status content within the
                                                                // PState.
                                                                .localTransform("$$accountIdToScheduledStatuses",
                                                                                Path.key("*authorId").must("*statusId",
                                                                                                "status")
                                                                                                .termVal("*status")),

                                                // Process the removal of a scheduled status.
                                                SubSource.create(RemoveStatus.class)
                                                                .macro(extractFields("*data", "*accountId",
                                                                                "*statusId"))
                                                                // Remove the scheduled status entry from the PState.
                                                                .localTransform("$$accountIdToScheduledStatuses",
                                                                                Path.key("*accountId", "*statusId")
                                                                                                .termVoid()),

                                                // Process editing the publish time of a scheduled status.
                                                SubSource.create(EditScheduledStatusPublishTime.class)
                                                                .macro(extractFields("*data", "*accountId", "*id",
                                                                                "*publishMillis"))
                                                                // Update the scheduled publish time for a specific
                                                                // status.
                                                                .localTransform("$$accountIdToScheduledStatuses",
                                                                                Path.key("*accountId").must("*id",
                                                                                                "publishMillis")
                                                                                                .termVal("*publishMillis"))
                                                                // Select the UUID for the scheduled status to ensure it
                                                                // is correctly identified
                                                                // in the schedule.
                                                                .localSelect("$$accountIdToScheduledStatuses",
                                                                                Path.key("*accountId").must("*id",
                                                                                                "uuid"))
                                                                .out("*uuid")
                                                                // Re-schedule the status post with the updated publish
                                                                // time.
                                                                .macro(scheduledStatuses.scheduleItem("*publishMillis",
                                                                                new Expr(Ops.TUPLE, "*accountId", "*id",
                                                                                                "*uuid"))));

                // Begin processing ticks for scheduled statuses.
                stream.source("*scheduledStatusTick")
                                // Apply a macro to handle the expiration of scheduled statuses.
                                .macro(scheduledStatuses.handleExpirations("*tuple", "*currentTimeMillis",
                                                // Expand each tuple to access individual fields: accountId, statusId
                                                // (id), and
                                                // uuid.
                                                Block.each(Ops.EXPAND, "*tuple").out("*accountId", "*id", "*uuid")
                                                                // Select the specific scheduled status information from
                                                                // the PState using
                                                                // accountId and id.
                                                                .localSelect("$$accountIdToScheduledStatuses",
                                                                                Path.key("*accountId", "*id"))
                                                                .out("*m")
                                                                // Check if the selected status information is not null,
                                                                // indicating a scheduled
                                                                // status exists.
                                                                .ifTrue(new Expr(Ops.IS_NOT_NULL, "*m"),
                                                                                // Extract the scheduled publish time
                                                                                // and status object from the status
                                                                                // information.
                                                                                Block.each(Ops.GET, "*m",
                                                                                                "publishMillis")
                                                                                                .out("*publishMillis")
                                                                                                .each(Ops.GET, "*m",
                                                                                                                "status")
                                                                                                .out("*status")
                                                                                                // Check if the current
                                                                                                // system time is
                                                                                                // greater than or equal
                                                                                                // to the
                                                                                                // scheduled publish
                                                                                                // time.
                                                                                                .ifTrue(new Expr(
                                                                                                                Ops.GREATER_THAN_OR_EQUAL,
                                                                                                                "*currentTimeMillis",
                                                                                                                "*publishMillis"),
                                                                                                                // Adjust
                                                                                                                // the
                                                                                                                // timestamp
                                                                                                                // of
                                                                                                                // the
                                                                                                                // status
                                                                                                                // to
                                                                                                                // the
                                                                                                                // current
                                                                                                                // time
                                                                                                                // and
                                                                                                                // handle
                                                                                                                // any
                                                                                                                // associated
                                                                                                                // poll
                                                                                                                // content.
                                                                                                                Block.each((Status status,
                                                                                                                                Long currentTimeMillis,
                                                                                                                                String uuid) -> {
                                                                                                                        long origTimestamp = status
                                                                                                                                        .getTimestamp();
                                                                                                                        status.setTimestamp(
                                                                                                                                        currentTimeMillis);
                                                                                                                        PollContent pc = ApolloHelpers
                                                                                                                                        .extractPollContent(
                                                                                                                                                        status);
                                                                                                                        if (pc != null)
                                                                                                                                pc.setExpirationMillis(
                                                                                                                                                pc.getExpirationMillis()
                                                                                                                                                                + Math
                                                                                                                                                                                .max(0, currentTimeMillis
                                                                                                                                                                                                - origTimestamp));
                                                                                                                        // Return
                                                                                                                        // a
                                                                                                                        // new
                                                                                                                        // AddStatus
                                                                                                                        // action
                                                                                                                        // to
                                                                                                                        // publish
                                                                                                                        // the
                                                                                                                        // status.
                                                                                                                        return new AddStatus(
                                                                                                                                        uuid,
                                                                                                                                        status);
                                                                                                                }, "*status", "*currentTimeMillis",
                                                                                                                                "*uuid")
                                                                                                                                .out("*addStatus")
                                                                                                                                // Append
                                                                                                                                // the
                                                                                                                                // ready-to-publish
                                                                                                                                // status
                                                                                                                                // to
                                                                                                                                // the
                                                                                                                                // statusDepot
                                                                                                                                // for
                                                                                                                                // actual
                                                                                                                                // publication.
                                                                                                                                .depotPartitionAppend(
                                                                                                                                                "*statusDepot",
                                                                                                                                                "*addStatus")
                                                                                                                                // Remove
                                                                                                                                // the
                                                                                                                                // scheduled
                                                                                                                                // status
                                                                                                                                // entry
                                                                                                                                // now
                                                                                                                                // that
                                                                                                                                // it's
                                                                                                                                // been
                                                                                                                                // processed.
                                                                                                                                .localTransform("$$accountIdToScheduledStatuses",
                                                                                                                                                Path.key("*accountId",
                                                                                                                                                                "*id")
                                                                                                                                                                .termVoid())))));

        }

        private static void declareReportsTopology(Topologies topologies) {
                StreamTopology stream = topologies.stream("reports");
                stream.pstate("$$reportIdToReport", PState.mapSchema(String.class, Report.class));
                stream.source("*reportDepot").out("*report")
                                .macro(extractFields("*report", "*id"))
                                .localTransform("$$reportIdToReport", Path.key("*id").termVal("*report"));
        }

        private static void declareApplicationTopology(Topologies topologies) {
                StreamTopology stream = topologies.stream("applications");
                // Declare a PState to map client IDs to Application objects
                stream.pstate("$$clientIdToApplication", PState.mapSchema(String.class, Application.class));
                // Source from the application depot
                stream.source("*applicationDepot").out("*application")
                                .localTransform("$$clientIdToApplication",
                                                Path.key(new Expr(Application::getClient_id, "*application"))
                                                                .termVal("*application"));
        }

        private static void declareActivityTopology(Topologies topologies) {
                StreamTopology stream = topologies.stream("activity");
                // Declare a PState to map client IDs to Application objects
                stream.pstate("$$accountIdToTimestamp", PState.mapSchema(Long.class, Long.class));
                // Source from the application depot
                stream.source("*userActivityDepot").out("*activity")
                                .macro(extractFields("*activity", "*accountId", "*timestamp"))
                                .localTransform("$$accountIdToTimestamp",
                                                Path.key("*accountId").termVal("*timestamp"));
        }

        private static void declareSpaceTopology(Topologies topologies) {
                StreamTopology stream = topologies.stream("space");
                stream.pstate("$$spaceIdToSpace", PState.mapSchema(String.class, Space.class));
                stream.source("*spaceDepot").out("*space")
                                .macro(extractFields("*space", "*id"))
                                .localTransform("$$spaceIdToSpace",
                                                Path.key("*id").termVal("*space"));
        }

        private void declareQueries(Topologies topologies) {

                // Defines a query topology named "getAccountTimeline" with inputs for
                // account IDs, starting status ID, a limit for the number of statuses, and a
                // flag for including replies.
                topologies.query("getAccountTimeline", "*requestAccountId", "*timelineAccountId", "*firstStatusId",
                                "*limit",
                                "*includeReplies").out("*results")
                                // Partition the data based on the timeline account ID to ensure that all
                                // operations related to a specific account are processed together.
                                .hashPartition("*timelineAccountId")
                                // For each status, generate sorted options based on the limit, excluding the
                                // starting point to avoid duplicate fetching.
                                .each((Integer limit) -> SortedRangeFromOptions.excludeStart().maxAmt(limit), "*limit")
                                .out("*sortedOptions")
                                // Select statuses from the account's timeline that fall within the specified
                                // range and sort options.
                                .localSelect("$$accountIdToAccountTimeline", Path.key("*timelineAccountId")
                                                .sortedSetRangeFrom("*firstStatusId", "*sortedOptions")
                                                .all())
                                .out("*statusId")
                                // Fetch the first instance of each selected status from the statuses PState.
                                .localSelect("$$accountIdToStatuses",
                                                Path.must("*timelineAccountId", "*statusId").first())
                                .out("*status")

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
                                                (Long authorId, Long statusId,
                                                                Boolean shouldExclude) -> new StatusPointer(authorId,
                                                                                statusId)
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
                                .invokeQuery("getStatusesFromPointers", "*requestAccountId", "*statusPointers",
                                                "*filterOptions")
                                .out("*statusQueryResults")
                                // Update the status query results based on the retrieved data, considering
                                // pagination and whether the data is refreshed.
                                .each(ApolloHelpers::updateStatusQueryResults, "*statusQueryResults", "*statusPointers",
                                                "*limit",
                                                false)
                                .out("*results");

                // Refreshes the home timeline for a given account. This includes fetching new
                // statuses from followers and self, potentially merging these two sources, and
                // applying visibility and content filters.
                topologies.query("refreshHomeTimeline", "*accountId").out("*ret")
                                // Set a reference point in the dataflow that can be returned to.
                                .anchor("RefreshRoot")

                                // Select data from a specific PState based on accountId, transforming the data
                                // as specified.
                                .select("$$followerToFolloweesById",
                                                Path.key("*accountId").sortedMapRangeFrom(0L, 300).mapVals())
                                .out("*followeeFollower")
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
                                                Path.key("*followee").sortedMapRangeFrom(0L, 30)
                                                                .transformed(Path.mapVals().term(Ops.FIRST)))
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
                                                                .keepTrue(new Expr(Ops.NOT_EQUAL, "*visibility",
                                                                                StatusVisibility.Direct)))
                                // Combine multiple fields into a tuple, outputting the result.
                                .each(Ops.TUPLE, "*timestamp", "*followee", "*statusId").out("*tuple")
                                // Assigns data to the original partition for processing.
                                .originPartition()
                                // Aggregates data based on specified criteria, sorting and limiting the output.
                                .agg(Agg.topMonotonic(timelineMaxAmount, "*tuple").sortValFunction(Ops.FIRST))
                                .out("*toAdd")
                                // Applies a function to update home timelines based on aggregated data.
                                .each(HomeTimelines::refreshStatuses, "*homeTimelines", "*accountId", "*toAdd")
                                .out("*ret");

                // Query topology for retrieves the home timeline for a given account, deciding
                // whether a refresh is needed based on certain conditions, then reading and
                // returning the timeline statuses with applied query filter options.
                topologies.query("getHomeTimeline", "*requestAccountId", "*firstStatusPointer", "*limit").out("*ret")
                                // Partition the data based on the request account ID to ensure related data is
                                // processed together.
                                .hashPartition("*requestAccountId")
                                // Conditionally refresh the home timeline if needed, based on the
                                // 'enableHomeTimelineRefresh' flag and whether the timeline needs a refresh.
                                .ifTrue(new Expr(Ops.AND, enableHomeTimelineRefresh,
                                                new Expr(HomeTimelines::needsRefresh, "*homeTimelines",
                                                                "*requestAccountId")),
                                                // If a refresh is needed, invoke the 'refreshHomeTimeline' query and
                                                // output the
                                                // result.
                                                Block.invokeQuery("refreshHomeTimeline", "*requestAccountId")
                                                                .each(Ops.IDENTITY, true).out("*refreshed"),
                                                // If no refresh is needed, proceed without changing the flow but mark
                                                // as not
                                                // refreshed.
                                                Block.each(Ops.IDENTITY, false).out("*refreshed"))
                                // Read the timeline data from storage, providing necessary parameters.
                                .each(HomeTimelines::readTimelineFrom, "*homeTimelines", "*requestAccountId",
                                                "*firstStatusPointer",
                                                "*limit")
                                .out("*statusPointers")
                                // Generate filter options for the query.
                                .each(() -> new QueryFilterOptions(FilterContext.Home, true)).out("*filterOptions")
                                // Query to get status updates from pointers, applying filter options.
                                .invokeQuery("getStatusesFromPointers", "*requestAccountId", "*statusPointers",
                                                "*filterOptions")
                                .out("*statusQueryResults")
                                // Update the status query results based on the refresh status, pointers, and
                                // limit.
                                .each(ApolloHelpers::updateStatusQueryResults, "*statusQueryResults", "*statusPointers",
                                                "*limit",
                                                "*refreshed")
                                .out("*ret")
                                // Return to the original partition for further processing or output.
                                .originPartition();

                // Define a query named "getHomeTimelinesUntil" which takes tuples of
                // [accountId, endStatusPointer] and a limit for each timeline, returning
                // statusIds sorted from newest to oldest.
                topologies.query("getHomeTimelinesUntil", "*tuples", "*limitPerTimeline").out("*ret")
                                // Explode the input tuples into indexed pairs to preserve the order of
                                // processing, outputting each index and tuple.
                                .each(Ops.EXPLODE_INDEXED, "*tuples").out("*i", "*tuple")
                                // Expand each tuple into its components: accountId and endStatusPointer.
                                .each(Ops.EXPAND, "*tuple").out("*accountId", "*endStatusPointer")
                                // Partition the processing by accountId, ensuring all operations related to a
                                // specific accountId are processed together.
                                .hashPartition("*accountId")
                                // Read timelines for each accountId up to the specified endStatusPointer,
                                // applying the limit for the number of returned status updates.
                                .each(HomeTimelines::readTimelineUntil, "*homeTimelines", "*accountId",
                                                "*endStatusPointer",
                                                "*limitPerTimeline")
                                .out("*newPointers")
                                // Return to the original partitioning scheme, typically to aggregate results
                                // correctly across all nodes.
                                .originPartition()
                                // Aggregate the new status pointers for each timeline into a map using the
                                // original index to preserve the input order, outputting the final result.
                                .agg(Agg.map("*i", "*newPointers")).out("*ret");

                // Defines a query topology named "getDirectTimeline" that fetches direct
                // messages for a specific account.
                // The query takes the account ID (*requestAccountId), the starting point for
                // the timeline (*firstTimelineIndex),
                // and the number of messages to fetch (*limit) as inputs and outputs the
                // results to "*results".
                topologies.query("getDirectTimeline", "*requestAccountId", "*firstTimelineIndex", "*limit")
                                .out("*results")
                                // Distributes the processing based on the account ID to ensure that all
                                // operations for a given account are handled by the same partition.
                                .hashPartition("*requestAccountId")
                                // Generates sorting options for fetching direct messages, excluding the start
                                // point and setting a maximum amount
                                // of messages to fetch as specified by *limit. This ensures that the query
                                // fetches messages newer than the *firstTimelineIndex.
                                .each((Integer limit) -> SortedRangeFromOptions.excludeStart().maxAmt(limit), "*limit")
                                .out("*sortedOptions")
                                // Performs a local selection on the persistent state
                                // "accountIdToDirectMessagesById" to fetch direct messages for the account.
                                // The selection is based on a sorted range from *firstTimelineIndex using the
                                // previously determined *sortedOptions,
                                // ensuring that the fetched messages are in the correct order and within the
                                // desired limit.
                                .localSelect("$$accountIdToDirectMessagesById",
                                                Path.subselect(Path.key("*requestAccountId")
                                                                .sortedMapRangeFrom("*firstTimelineIndex",
                                                                                "*sortedOptions")
                                                                .mapVals()))
                                .out("*statusPointers")
                                // Prepares query filter options tailored for threading context, indicating that
                                // only messages relevant to direct messaging threads should be fetched.
                                .each(() -> new QueryFilterOptions(FilterContext.Thread, true)).out("*filterOptions")
                                // Invokes another query topology "getStatusesFromPointers" to actually fetch
                                // the messages based on the pointers obtained from the local select.
                                // This query uses the filter options prepared in the previous step to ensure
                                // that only relevant messages are fetched.
                                .invokeQuery("getStatusesFromPointers", "*requestAccountId", "*statusPointers",
                                                "*filterOptions")
                                .out("*statusQueryResults")
                                // Updates the status query results with additional processing, if necessary, by
                                // ApolloHelpers::updateStatusQueryResults function.
                                // This step can include operations like filtering, enriching, or formatting the
                                // fetched messages before they are returned as the final result.
                                .each(ApolloHelpers::updateStatusQueryResults, "*statusQueryResults", "*statusPointers",
                                                "*limit",
                                                false)
                                .out("*results")
                                // Ensures that after all operations are complete, any further processing or
                                // aggregation takes place in the original partition where the query started.
                                .originPartition();

                // Defines a query topology named "getConversationTimeline" for fetching
                // conversations related to a specific account.
                // Inputs include the account ID (*requestAccountId), starting timeline index
                // (*firstTimelineIndex), and the number of conversations to fetch (*limit).
                topologies.query("getConversationTimeline", "*requestAccountId", "*firstTimelineIndex", "*limit")
                                .out("*results")
                                // Partitions processing based on the account ID to ensure related operations
                                // for a given account are processed together.
                                .hashPartition("*requestAccountId")
                                // Generates sorting options for fetching conversations, excluding the start
                                // point and setting a maximum amount
                                // defined by *limit to control the number of conversations fetched.
                                .each((Integer limit) -> SortedRangeFromOptions.excludeStart().maxAmt(limit), "*limit")
                                .out("*sortedOptions")
                                // Selects conversation IDs associated with the account from the persistent
                                // state "accountIdToConvoIdsById",
                                // using a sorted range from *firstTimelineIndex based on *sortedOptions. It
                                // fetches all entries within the range.
                                .localSelect("$$accountIdToConvoIdsById", Path.key("*requestAccountId")
                                                .sortedMapRangeFrom("*firstTimelineIndex", "*sortedOptions")
                                                .all())
                                .out("*timelineIndexAndConvoId")
                                // Expands each tuple of timeline index and conversation ID for individual
                                // processing.
                                .each(Ops.EXPAND, "*timelineIndexAndConvoId").out("*timelineIndex", "*convoId")
                                // Invokes another query "getConversation" to fetch the conversation object
                                // based on *convoId for each entry.
                                .invokeQuery("getConversation", "*requestAccountId", "*convoId").out("*conversation")
                                // Wraps each conversation along with its timeline index into an
                                // IndexedConversation object for sorting.
                                .each((RamaFunction2<Long, Conversation, IndexedConversation>) IndexedConversation::new,
                                                "*timelineIndex", "*conversation")
                                .out("*indexedConversation")
                                // Returns processing to the original partition where the query was initiated.
                                .originPartition()
                                // Aggregates all IndexedConversation objects into an unsorted list.
                                .agg(Agg.list("*indexedConversation")).out("*unsortedResults")
                                // Sorts the aggregated list of IndexedConversations by their indices to ensure
                                // they are in the correct order,
                                // then maps the sorted list to just the conversation objects.
                                .each((List<IndexedConversation> unsortedResults) -> {
                                        ArrayList<IndexedConversation> results = new ArrayList<>(unsortedResults);
                                        results.sort((IndexedConversation a,
                                                        IndexedConversation b) -> (int) (a.index - b.index));
                                        return results.stream().map(o -> o.conversation).collect(Collectors.toList());
                                }, "*unsortedResults").out("*results");

                // Starts the query to fetch conversation details for a given account and
                // conversation ID.
                topologies.query("getConversation", "*requestAccountId", "*convoId").out("*conversation")
                                // Partitions processing by the request account ID to ensure related operations
                                // are handled together.
                                .hashPartition("*requestAccountId")
                                // Checks for the existence of keys related to the conversation in the
                                // persistent state.
                                .localSelect("$$accountIdToConvoIdToConvo",
                                                Path.subselect(Path.key("*requestAccountId", "*convoId").mapKeys()))
                                .out("*keys")
                                // Conditional check: if no keys exist (implying no conversation data), set the
                                // conversation output to null.
                                .ifTrue(new Expr(Ops.EQUAL, 0, new Expr(Ops.SIZE, "*keys")),
                                                Block.each(Ops.IDENTITY, null).out("*conversation"),
                                                // If keys exist, fetch the first status from the conversation timeline
                                                // as a
                                                // starting point for fetching the latest status.
                                                Block.localSelect("$$accountIdToConvoIdToConvo",
                                                                Path.key("*requestAccountId", "*convoId", "timeline")
                                                                                .view(Ops.FIRST))
                                                                .out("*statusIndexAndStatusPointer")
                                                                .ifTrue(new Expr(Ops.IS_NULL,
                                                                                "*statusIndexAndStatusPointer"),
                                                                                // If no status index and pointer are
                                                                                // found, set the last status of the
                                                                                // conversation to null.
                                                                                Block.each(Ops.IDENTITY, null)
                                                                                                .out("*lastStatus"),
                                                                                // If a status index and pointer exist,
                                                                                // proceed to fetch the status details.
                                                                                Block.each(Ops.EXPAND,
                                                                                                "*statusIndexAndStatusPointer")
                                                                                                .out("*statusIndex",
                                                                                                                "*statusPointer")
                                                                                                .each(Ops.TUPLE, "*statusPointer")
                                                                                                .out("*statusPointers")
                                                                                                .each(() -> new QueryFilterOptions(
                                                                                                                FilterContext.Thread,
                                                                                                                false))
                                                                                                .out("*filterOptions")
                                                                                                .invokeQuery("getStatusesFromPointers",
                                                                                                                "*requestAccountId",
                                                                                                                "*statusPointers",
                                                                                                                "*filterOptions")
                                                                                                .out("*statusQueryResults")
                                                                                                .each((StatusQueryResults statusQueryResults) -> {
                                                                                                        // Extracts the
                                                                                                        // first
                                                                                                        // (latest)
                                                                                                        // status from
                                                                                                        // the results,
                                                                                                        // if any;
                                                                                                        // otherwise,
                                                                                                        // returns null.
                                                                                                        if (statusQueryResults.results
                                                                                                                        .size() == 0)
                                                                                                                return null;
                                                                                                        else
                                                                                                                return new StatusQueryResult(
                                                                                                                                statusQueryResults.results
                                                                                                                                                .get(0),
                                                                                                                                statusQueryResults.mentions);
                                                                                                }, "*statusQueryResults")
                                                                                                .out("*lastStatus"))
                                                                // Fetches additional conversation details such as
                                                                // unread status and account IDs
                                                                // of participants.
                                                                .localSelect("$$accountIdToConvoIdToConvo",
                                                                                Path.key("*requestAccountId",
                                                                                                "*convoId")
                                                                                                .subselect(Path.multiPath(
                                                                                                                Path.key("unread"),
                                                                                                                Path.key("accountIds"))))
                                                                .out("*tuple")
                                                                .each(Ops.EXPAND, "*tuple")
                                                                .out("*unread", "*accountIds")
                                                                // Queries for account details of the participants using
                                                                // their IDs.
                                                                .invokeQuery("getAccountsFromAccountIds", null,
                                                                                "*accountIds")
                                                                .out("*accounts")
                                                                // Compiles the final conversation object with all
                                                                // details, including the latest
                                                                // status if available.
                                                                .each((Long cid, Boolean unread,
                                                                                List<AccountWithId> accounts,
                                                                                StatusQueryResult lastStatus) -> {
                                                                        Conversation convo = new Conversation(cid,
                                                                                        unread, accounts);
                                                                        if (lastStatus != null)
                                                                                convo.setLastStatus(lastStatus);
                                                                        return convo;
                                                                }, "*convoId", "*unread", "*accounts", "*lastStatus")
                                                                .out("*conversation"))
                                // Returns processing to the original partition for the final aggregation or
                                // output.
                                .originPartition();

                // Defines a query topology for fetching ancestor statuses of a given status in
                // a social networking context.
                topologies.query("getAncestors", "*requestAccountId", "*childAuthorId", "*childStatusId", "*limit")
                                .out("*results")
                                // Converts the authorId and statusId into a StatusPointer object for further
                                // processing.
                                .each((Long authorId, Long statusId) -> new StatusPointer(authorId, statusId),
                                                "*childAuthorId",
                                                "*childStatusId")
                                .out("*initialStatusPointer")
                                // Initializes a loop to fetch parent statuses recursively, starting with the
                                // initial status pointer.
                                // Loop variables include a count of fetched statuses, the current status
                                // pointer being processed,
                                // and a list of status pointers collected so far.
                                .loopWithVars(LoopVars.var("*count", 0)
                                                .var("*statusPointer", "*initialStatusPointer")
                                                .var("*statusPointers", PersistentList.EMPTY),
                                                Block.ifTrue(new Expr(Ops.GREATER_THAN_OR_EQUAL, "*count", "*limit"),
                                                                // If the count reaches the limit, emit the collected
                                                                // status pointers and
                                                                // terminate the loop.
                                                                Block.emitLoop("*statusPointers"),
                                                                // Otherwise, continue fetching ancestor statuses.
                                                                Block.macro(extractFields("*statusPointer", "*authorId",
                                                                                "*statusId"))
                                                                                .select("$$accountIdToStatuses",
                                                                                                Path.key("*authorId",
                                                                                                                "*statusId")
                                                                                                                .view(Ops.FIRST))
                                                                                .out("*status")
                                                                                // If no status is found (null), emit
                                                                                // the collected status pointers and
                                                                                // terminate the loop.
                                                                                .ifTrue(new Expr(Ops.IS_NULL,
                                                                                                "*status"),
                                                                                                Block.emitLoop("*statusPointers"),
                                                                                                // If the status is not
                                                                                                // the original child
                                                                                                // status, increment the
                                                                                                // count
                                                                                                // and add the status
                                                                                                // pointer to the
                                                                                                // collection.
                                                                                                Block.ifTrue(new Expr(
                                                                                                                Ops.NOT_EQUAL,
                                                                                                                "*statusId",
                                                                                                                "*childStatusId"),
                                                                                                                Block.each(Ops.INC,
                                                                                                                                "*count")
                                                                                                                                .out("*nextCount")
                                                                                                                                .each((IPersistentList statusPointers,
                                                                                                                                                StatusPointer statusPointer) -> statusPointers
                                                                                                                                                                .cons(statusPointer),
                                                                                                                                                "*statusPointers",
                                                                                                                                                "*statusPointer")
                                                                                                                                .out("*nextStatusPointers"),
                                                                                                                // If
                                                                                                                // it's
                                                                                                                // the
                                                                                                                // original
                                                                                                                // status,
                                                                                                                // do
                                                                                                                // not
                                                                                                                // modify
                                                                                                                // the
                                                                                                                // count
                                                                                                                // or
                                                                                                                // the
                                                                                                                // collected
                                                                                                                // pointers.
                                                                                                                Block.each(Ops.IDENTITY,
                                                                                                                                "*count")
                                                                                                                                .out("*nextCount")
                                                                                                                                .each(Ops.IDENTITY,
                                                                                                                                                "*statusPointers")
                                                                                                                                .out("*nextStatusPointers"))
                                                                                                                // Check
                                                                                                                // if
                                                                                                                // the
                                                                                                                // current
                                                                                                                // status
                                                                                                                // has a
                                                                                                                // parent
                                                                                                                // (indicating
                                                                                                                // it's
                                                                                                                // a
                                                                                                                // reply).

                                                                                                                .macro(extractFields(
                                                                                                                                "*status",
                                                                                                                                "*content"))
                                                                                                                .ifTrue(new Expr(
                                                                                                                                Ops.IS_INSTANCE_OF,
                                                                                                                                ReplyStatusContent.class,
                                                                                                                                "*content"),
                                                                                                                                // If
                                                                                                                                // so,
                                                                                                                                // extract
                                                                                                                                // the
                                                                                                                                // parent
                                                                                                                                // status
                                                                                                                                // pointer,
                                                                                                                                // prepare
                                                                                                                                // the
                                                                                                                                // next
                                                                                                                                // iteration
                                                                                                                                // variables,
                                                                                                                                // and
                                                                                                                                // continue
                                                                                                                                // the
                                                                                                                                // loop
                                                                                                                                // with
                                                                                                                                // the
                                                                                                                                // parent
                                                                                                                                // status
                                                                                                                                // pointer.
                                                                                                                                Block.macro(extractFields(
                                                                                                                                                "*content",
                                                                                                                                                "*parent"))
                                                                                                                                                .each((StatusPointer parent) -> parent.authorId,
                                                                                                                                                                "*parent")
                                                                                                                                                .out("*parentAuthorId")
                                                                                                                                                .each((StatusPointer parent) -> parent.statusId,
                                                                                                                                                                "*parent")
                                                                                                                                                .out("*parentStatusId")
                                                                                                                                                .each((Long authorId,
                                                                                                                                                                Long statusId) -> new StatusPointer(
                                                                                                                                                                                authorId,
                                                                                                                                                                                statusId),
                                                                                                                                                                "*parentAuthorId",
                                                                                                                                                                "*parentStatusId")
                                                                                                                                                .out("*nextStatusPointer")
                                                                                                                                                .continueLoop("*nextCount",
                                                                                                                                                                "*nextStatusPointer",
                                                                                                                                                                "*nextStatusPointers"),
                                                                                                                                // If
                                                                                                                                // there's
                                                                                                                                // no
                                                                                                                                // parent,
                                                                                                                                // emit
                                                                                                                                // the
                                                                                                                                // collected
                                                                                                                                // status
                                                                                                                                // pointers
                                                                                                                                // and
                                                                                                                                // terminate
                                                                                                                                // the
                                                                                                                                // loop.
                                                                                                                                Block.emitLoop("*nextStatusPointers")))))
                                .out("*statusPointers")
                                // Prepare filter options for fetching status content.
                                .each(() -> new QueryFilterOptions(FilterContext.Public, true)).out("*filterOptions")
                                // Fetches the actual status contents based on the collected status pointers,
                                // applying the prepared filter options.
                                .invokeQuery("getStatusesFromPointers", "*requestAccountId", "*statusPointers",
                                                "*filterOptions")
                                .out("*results")
                                // Ensures the processing returns to the original partition for final output
                                // aggregation or processing.
                                .originPartition();

                topologies.query("getDescendants", "*requestAccountId", "*parentAuthorId", "*parentStatusId", "*limit")
                                .out("*results")
                                .each((Long authorId, Long statusId) -> Arrays
                                                .asList(new StatusPointer(authorId, statusId)),
                                                "*parentAuthorId", "*parentStatusId")
                                .out("*initialStatusPointers")
                                .loopWithVars(LoopVars.var("*count", 0)
                                                .var("*statusPointerQueue", "*initialStatusPointers")
                                                .var("*statusPointers", PersistentVector.EMPTY),
                                                Block.each((List<StatusPointer> descendants) -> descendants.get(0),
                                                                "*statusPointerQueue")
                                                                .out("*nextStatusPointer")
                                                                .ifTrue(new Expr(Ops.GREATER_THAN_OR_EQUAL, "*count",
                                                                                "*limit"),
                                                                                Block.emitLoop("*statusPointers"),
                                                                                Block.macro(extractFields(
                                                                                                "*nextStatusPointer",
                                                                                                "*authorId",
                                                                                                "*statusId"))
                                                                                                .select("$$accountIdToStatuses",
                                                                                                                Path.key("*authorId",
                                                                                                                                "*statusId")
                                                                                                                                .view(Ops.FIRST))
                                                                                                .out("*nextStatus")
                                                                                                .ifTrue(new Expr(
                                                                                                                Ops.IS_NULL,
                                                                                                                "*nextStatus"),
                                                                                                                Block.emitLoop("*statusPointers"),

                                                                                                                // add
                                                                                                                // status
                                                                                                                // to
                                                                                                                // the
                                                                                                                // output
                                                                                                                // if
                                                                                                                // it's
                                                                                                                // not
                                                                                                                // the
                                                                                                                // original
                                                                                                                // status
                                                                                                                Block.ifTrue(
                                                                                                                                new Expr(Ops.NOT_EQUAL,
                                                                                                                                                "*statusId",
                                                                                                                                                "*parentStatusId"),
                                                                                                                                Block.each(Ops.INC,
                                                                                                                                                "*count")
                                                                                                                                                .out("*nextCount")
                                                                                                                                                .each((PersistentVector statusPointers,
                                                                                                                                                                Long authorId,
                                                                                                                                                                Long statusId) -> statusPointers
                                                                                                                                                                                .cons(
                                                                                                                                                                                                new StatusPointer(
                                                                                                                                                                                                                authorId,
                                                                                                                                                                                                                statusId)),
                                                                                                                                                                "*statusPointers",
                                                                                                                                                                "*authorId",
                                                                                                                                                                "*statusId")
                                                                                                                                                .out("*nextStatusPointers"),
                                                                                                                                Block.each(Ops.IDENTITY,
                                                                                                                                                "*count")
                                                                                                                                                .out("*nextCount")
                                                                                                                                                .each(Ops.IDENTITY,
                                                                                                                                                                "*statusPointers")
                                                                                                                                                .out("*nextStatusPointers"))

                                                                                                                                // get
                                                                                                                                // the
                                                                                                                                // direct
                                                                                                                                // children
                                                                                                                                // of
                                                                                                                                // the
                                                                                                                                // status
                                                                                                                                .localSelect("$$statusIdToRepliesById",
                                                                                                                                                Path.subselect(
                                                                                                                                                                Path.key("*statusId")
                                                                                                                                                                                .sortedMapRangeFrom(
                                                                                                                                                                                                0L,
                                                                                                                                                                                                "*limit")
                                                                                                                                                                                .mapVals()))
                                                                                                                                .out("*childStatusPointers")

                                                                                                                                // remove
                                                                                                                                // the
                                                                                                                                // first
                                                                                                                                // item
                                                                                                                                // from
                                                                                                                                // the
                                                                                                                                // list
                                                                                                                                // since
                                                                                                                                // we
                                                                                                                                // just
                                                                                                                                // processed
                                                                                                                                // it,
                                                                                                                                // then
                                                                                                                                // prepend
                                                                                                                                // its
                                                                                                                                // children
                                                                                                                                // to
                                                                                                                                // the
                                                                                                                                // list,
                                                                                                                                // so
                                                                                                                                // we
                                                                                                                                // get
                                                                                                                                // to
                                                                                                                                // them
                                                                                                                                // first
                                                                                                                                // (depth-first)
                                                                                                                                .each((List<StatusPointer> descendants,
                                                                                                                                                List<StatusPointer> children) -> {
                                                                                                                                        ArrayList<StatusPointer> results = new ArrayList<>(
                                                                                                                                                        children);
                                                                                                                                        Iterator<StatusPointer> iter = descendants
                                                                                                                                                        .iterator();
                                                                                                                                        if (iter.hasNext()) {
                                                                                                                                                iter.next(); // skip
                                                                                                                                                             // the
                                                                                                                                                             // first
                                                                                                                                                             // one
                                                                                                                                                while (iter.hasNext()
                                                                                                                                                                && results
                                                                                                                                                                                .size() < DESCENDANT_SEARCH_LIMIT)
                                                                                                                                                        results.add(iter.next());
                                                                                                                                        }
                                                                                                                                        return results;
                                                                                                                                }, "*statusPointerQueue",
                                                                                                                                                "*childStatusPointers")
                                                                                                                                .out("*nextStatusPointerQueue")

                                                                                                                                // continue
                                                                                                                                // the
                                                                                                                                // loop
                                                                                                                                // if
                                                                                                                                // there
                                                                                                                                // is
                                                                                                                                // more
                                                                                                                                // in
                                                                                                                                // the
                                                                                                                                // queue
                                                                                                                                .ifTrue(new Expr(
                                                                                                                                                Ops.GREATER_THAN,
                                                                                                                                                new Expr(Ops.SIZE,
                                                                                                                                                                "*nextStatusPointerQueue"),
                                                                                                                                                0),
                                                                                                                                                Block.continueLoop(
                                                                                                                                                                "*nextCount",
                                                                                                                                                                "*nextStatusPointerQueue",
                                                                                                                                                                "*nextStatusPointers"),
                                                                                                                                                Block.emitLoop("*nextStatusPointers")))))
                                .out("*statusPointers")
                                .each(() -> new QueryFilterOptions(FilterContext.Public, true)).out("*filterOptions")
                                .invokeQuery("getStatusesFromPointers", "*requestAccountId", "*statusPointers",
                                                "*filterOptions")
                                .out("*results")
                                .originPartition();

                // Define a query topology named "getAccountMetadata" with input parameters for
                // the requester's account ID and the target account ID.
                topologies.query("getAccountMetadata", "*requestAccountId", "*accountId").out("*result")
                                // Distribute the processing of this query based on the "*accountId" to ensure
                                // that all data related to a specific account is processed together.
                                .hashPartition("*accountId")
                                // Select the total count of statuses for the target account by accessing a
                                // predefined state or structure "$$accountIdToAccountTimeline"
                                // and viewing its size. The result is output to "*statusCount".
                                .localSelect("$$accountIdToAccountTimeline", Path.key("*accountId").view(Ops.SIZE))
                                .out("*statusCount")
                                // Select the last status posted by the target account from
                                // "$$accountIdToStatuses" by navigating through its structure and
                                // fetching the first element. The result is output to "*lastStatus".
                                .localSelect("$$accountIdToStatuses",
                                                Path.key("*accountId").subselect(Path.first().last().first())
                                                                .view(Ops.FIRST))
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
                                .each((Integer statusCount, Integer followerCount, Integer followeeCount,
                                                Boolean isFollowedByRequester,
                                                Boolean isFollowingRequester, Status lastStatus) -> {
                                        AccountMetadata metadata = new AccountMetadata(statusCount, followerCount,
                                                        followeeCount,
                                                        isFollowedByRequester, isFollowingRequester);
                                        if (lastStatus != null)
                                                metadata.setLastStatusTimestamp(lastStatus.timestamp);
                                        return metadata;
                                },
                                                "*statusCount", "*followerCount", "*followeeCount",
                                                "*isFollowedByRequester",
                                                "*isFollowingRequester",
                                                "*lastStatus")
                                .out("*result")
                                // Return processing to the original partitioning scheme.
                                .originPartition();

                // Defines a query topology to fetch metadata associated with a list of account
                // IDs.
                // Inputs include the account making the request (*requestAccountId) and a list
                // of account IDs for which metadata is to be fetched (*accountIds).
                topologies.query("getAccountIdToMetadata", "*requestAccountId", "*accountIds")
                                .out("*accountIdToMetadata")
                                // Iterates over the list of account IDs, processing each ID individually.
                                .each(Ops.EXPLODE, "*accountIds").out("*accountId")
                                // Invokes a query named "getAccountMetadata" for each account ID to retrieve
                                // its metadata.
                                .invokeQuery("getAccountMetadata", "*requestAccountId", "*accountId").out("*metadata")
                                // Pairs each account ID with its retrieved metadata into tuples for
                                // aggregation.
                                .each(Ops.TUPLE, "*accountId", "*metadata").out("*accountIdAndMetadata")
                                // Ensures processing returns to the original partition where the query was
                                // initiated for final aggregation.
                                .originPartition()
                                // Aggregates all accountId-and-metadata tuples into a list for transformation.
                                .agg(Agg.list("*accountIdAndMetadata")).out("*accountIdAndMetadatas")
                                // Transforms the aggregated list of tuples into a map, associating each account
                                // ID with its metadata.
                                .each((List<List> accountIdAndMetadatas) -> {
                                        Map<Long, AccountMetadata> accountIdToMetadata = new HashMap<>();
                                        for (List accountIdAndMetadata : accountIdAndMetadatas) {
                                                long accountId = (long) accountIdAndMetadata.get(0); // Extracts the
                                                                                                     // account ID from
                                                                                                     // the tuple.
                                                AccountMetadata metadata = (AccountMetadata) accountIdAndMetadata
                                                                .get(1); // Extracts the
                                                                         // metadata from the
                                                                         // tuple.
                                                accountIdToMetadata.put(accountId, metadata); // Maps the account ID to
                                                                                              // its metadata.
                                        }
                                        return accountIdToMetadata; // Returns the constructed map as the final output.
                                }, "*accountIdAndMetadatas").out("*accountIdToMetadata");

                // Starts a query named "getAccountsFromAccountIds" to fetch detailed account
                // information and metadata for a given list of account IDs.
                // Inputs include the requesting account ID (*requestAccountId) and a list of
                // account IDs (*accountIds) whose details are to be fetched.
                topologies.query("getAccountsFromAccountIds", "*requestAccountId", "*accountIds").out("*results")
                                // Iterates over each account ID with its index for later reordering.
                                .each(Ops.EXPLODE_INDEXED, "*accountIds").out("*index", "*accountId")
                                // Selects each account's basic information from a persistent state, ensuring
                                // the account ID is not null.
                                .select("$$accountIdToAccount", Path.key("*accountId").filterPred(Ops.IS_NOT_NULL))
                                .out("*account")
                                // Checks if the requesting account ID is not null and then selects suppression
                                // information to determine if the account is blocked or muted.
                                .ifTrue(new Expr(Ops.IS_NOT_NULL, "*requestAccountId"),
                                                Block.select("$$accountIdToSuppressions",
                                                                Relationships.isBlockedOrMutedPath("*requestAccountId",
                                                                                "*accountId", false))
                                                                .out("*isBlockedOrMuted")
                                                                .keepTrue(new Expr(Ops.NOT, "*isBlockedOrMuted"))) // Keeps
                                                                                                                   // the
                                                                                                                   // account
                                                                                                                   // only
                                                                                                                   // if
                                                                                                                   // it
                                                                                                                   // is
                                                                                                                   // not
                                                                                                                   // blocked
                                                                                                                   // or
                                                                                                                   // muted.
                                // Invokes another query to fetch metadata for each account.
                                .invokeQuery("getAccountMetadata", "*requestAccountId", "*accountId").out("*metadata")
                                // Constructs an AccountWithId object combining account ID, basic account
                                // information, and metadata.
                                .each((RamaFunction3<Long, Account, AccountMetadata, AccountWithId>) AccountWithId::new,
                                                "*accountId",
                                                "*account", "*metadata")
                                .out("*accountWithId")
                                // Wraps each AccountWithId object with its original index for sorting.
                                .each((RamaFunction2<Integer, AccountWithId, IndexedAccountWithId>) IndexedAccountWithId::new,
                                                "*index",
                                                "*accountWithId")
                                .out("*indexedAccountWithId")
                                // Returns processing to the original partition where the query was initiated
                                // for final aggregation.
                                .originPartition()
                                // Aggregates all IndexedAccountWithId objects into an unsorted list.
                                .agg(Agg.list("*indexedAccountWithId")).out("*unsortedResults")
                                // Sorts the aggregated list by index to restore the original order of account
                                // IDs.
                                .each((List<IndexedAccountWithId> unsortedResults) -> {
                                        ArrayList<IndexedAccountWithId> results = new ArrayList<>(unsortedResults);
                                        results.sort((IndexedAccountWithId a,
                                                        IndexedAccountWithId b) -> (int) (a.index - b.index));
                                        return results.stream().map(o -> o.accountWithId).collect(Collectors.toList()); // Extracts
                                                                                                                        // and
                                                                                                                        // returns
                                                                                                                        // sorted
                                                                                                                        // AccountWithId
                                                                                                                        // objects.
                                }, "*unsortedResults").out("*results");

                // Starts a query named "getAccountsFromNames" to fetch accounts and their
                // metadata based on a given list of usernames.
                // Inputs include the account making the request (*requestAccountId) and a list
                // of usernames (*names).
                topologies.query("getAccountsFromNames", "*requestAccountId", "*names").out("*results")
                                // Iterates over each name with its index to maintain order for later sorting.
                                .each(Ops.EXPLODE_INDEXED, "*names").out("*index", "*name")
                                // For each username, selects the associated account ID from a persistent state
                                // that maps names to user accounts.
                                .select("$$nameToUser", Path.key("*name").must("accountId")).out("*accountId")
                                // Invokes a query named "getAccountMetadata" to fetch metadata for the account
                                // ID obtained from the username.
                                .invokeQuery("getAccountMetadata", "*requestAccountId", "*accountId").out("*metadata")
                                // Selects the account details for the account ID, ensuring the account ID is
                                // not null.
                                .select("$$accountIdToAccount", Path.key("*accountId").filterPred(Ops.IS_NOT_NULL))
                                .out("*account")
                                // Constructs an AccountWithId object by combining the account ID, account
                                // details, and metadata.
                                .each((RamaFunction3<Long, Account, AccountMetadata, AccountWithId>) AccountWithId::new,
                                                "*accountId",
                                                "*account", "*metadata")
                                .out("*accountWithId")
                                // Wraps each AccountWithId object with its original index for subsequent
                                // sorting.
                                .each((RamaFunction2<Integer, AccountWithId, IndexedAccountWithId>) IndexedAccountWithId::new,
                                                "*index",
                                                "*accountWithId")
                                .out("*indexedAccountWithId")
                                // Ensures the final aggregation or output occurs in the original partition
                                // where the query was initiated.
                                .originPartition()
                                // Aggregates all IndexedAccountWithId objects into an unsorted list for
                                // transformation.
                                .agg(Agg.list("*indexedAccountWithId")).out("*unsortedResults")
                                // Sorts the unsorted results by the original index of each name to restore the
                                // order as in the input list.
                                .each((List<IndexedAccountWithId> unsortedResults) -> {
                                        ArrayList<IndexedAccountWithId> results = new ArrayList<>(unsortedResults);
                                        results.sort((IndexedAccountWithId a,
                                                        IndexedAccountWithId b) -> (int) (a.index - b.index));
                                        return results.stream().map(o -> o.accountWithId).collect(Collectors.toList()); // Extracts
                                                                                                                        // and
                                                                                                                        // returns
                                                                                                                        // sorted
                                                                                                                        // AccountWithId
                                                                                                                        // objects.
                                }, "*unsortedResults").out("*results");

                // Defines a query named "getAccountsFromMentions" to fetch account details and
                // metadata for users mentioned in statuses.
                // Inputs include the account making the request (*requestAccountId) and a
                // collection of status results that may include mentions
                // (*statusResultWithIds).
                topologies.query("getAccountsFromMentions", "*requestAccountId", "*statusResultWithIds").out("*results")
                                // Extracts mentions from the collection of status results. A mention typically
                                // includes a username or an identifier that can be used to look up an account.
                                .each(ApolloHelpers::getMentionsFromStatusResults, "*statusResultWithIds")
                                .out("*mentions")
                                // Iterates over each mention, processing them individually.
                                .each(Ops.EXPLODE, "*mentions").out("*mention")
                                // For each mention, looks up the associated account ID from a persistent state
                                // that maps usernames or mention identifiers to user accounts.
                                .select("$$nameToUser", Path.key("*mention").must("accountId")).out("*accountId")
                                // Fetches metadata for the account corresponding to the mention. This could
                                // include additional details not stored directly with the account object.
                                .invokeQuery("getAccountMetadata", "*requestAccountId", "*accountId").out("*metadata")
                                // Selects the account object for the account ID, ensuring that the account
                                // exists (the account ID is not null).
                                .select("$$accountIdToAccount", Path.key("*accountId").filterPred(Ops.IS_NOT_NULL))
                                .out("*account")
                                // Constructs an AccountWithId object for each account by combining the account
                                // ID, the account details, and the fetched metadata.
                                .each((RamaFunction3<Long, Account, AccountMetadata, AccountWithId>) AccountWithId::new,
                                                "*accountId",
                                                "*account", "*metadata")
                                .out("*accountWithId")
                                // Ensures that after all operations are complete, any further processing or
                                // aggregation takes place in the original partition where the query started.
                                .originPartition()
                                // Aggregates all the AccountWithId objects into a list, which serves as the
                                // final output containing all mentioned accounts with their details and
                                // metadata.
                                .agg(Agg.list("*accountWithId")).out("*results");

                topologies.query("getStatusesFromPointers", "*requestAccountId", "*statusPointers", "*filterOptions")
                                .out("*results")
                                .ifTrue(new Expr(Ops.IS_NOT_NULL, "*requestAccountId"),
                                                Block.select("$$accountIdToFilterIdToFilter",
                                                                Path.key("*requestAccountId").subselect(Path.all()))
                                                                .out("*filterIdsAndFilters"),
                                                Block.each(Ops.IDENTITY, PersistentList.EMPTY)
                                                                .out("*filterIdsAndFilters"))
                                .each(ApolloHelpers::createFiltersWithIds, "*filterIdsAndFilters").out("*filters")

                                .macro(extractFields("*filterOptions", "*filterContext", "*excludeBlockedAndMuted"))
                                .each(FilterContext::getValue, "*filterContext").out("*filterContextValue")

                                .each(Ops.EXPLODE_INDEXED, "*statusPointers").out("*index", "*statusPointer")
                                .macro(extractFields("*statusPointer", "*authorId", "*statusId", "*shouldExclude"))

                                // stop if the status pointer is marked as excluded
                                .keepTrue(new Expr(Ops.NOT, "*shouldExclude"))

                                // stop if status author is blocked/muted
                                .ifTrue(new Expr(Ops.AND, "*excludeBlockedAndMuted",
                                                new Expr(Ops.IS_NOT_NULL, "*requestAccountId"),
                                                new Expr(Ops.NOT_EQUAL, "*requestAccountId", "*authorId")),
                                                Block.select("$$accountIdToSuppressions",
                                                                Relationships.isBlockedOrMutedPath("*requestAccountId",
                                                                                "*authorId", false))
                                                                .out("*isBlockedOrMuted")
                                                                .keepTrue(new Expr(Ops.NOT, "*isBlockedOrMuted"))
                                                                .select("$$accountIdToSuppressions",
                                                                                Path.key("*authorId", "blocked").view(
                                                                                                Ops.CONTAINS,
                                                                                                "*requestAccountId")
                                                                                                .filterPred(Ops.NOT)))

                                // get the status
                                .select("$$accountIdToStatuses", Path.key("*authorId", "*statusId")).out("*statusEdits")
                                .each(Ops.FIRST, "*statusEdits").out("*status")
                                .keepTrue(new Expr(Ops.IS_NOT_NULL, "*status"))
                                // get the original status if there were any edits
                                .ifTrue(new Expr(Ops.GREATER_THAN, new Expr(Ops.SIZE, "*statusEdits"), 1),
                                                Block.each(Ops.LAST, "*statusEdits").out("*originalStatus"),
                                                Block.each(Ops.IDENTITY, null).out("*originalStatus"))
                                // resolve the status
                                .macro(extractFields("*status", "*content"))
                                .macro(ApolloHelpers.resolveStatusResult("*requestAccountId", "*filters",
                                                "*filterContextValue",
                                                "*authorId", "*statusId", "*status", "*content", "*statusResult"))

                                // if the status is boosting a blocked or muted author, we need to drop it
                                .ifTrue(new Expr(Ops.AND, "*excludeBlockedAndMuted",
                                                new Expr((StatusResult s) -> s.getContent().isSetBoost(),
                                                                "*statusResult"),
                                                new Expr(Ops.NOT_EQUAL, "*requestAccountId", "*authorId")),
                                                Block.macro(extractFields("*content", "*boosted"))
                                                                .each((StatusPointer boosted) -> boosted.authorId,
                                                                                "*boosted")
                                                                .out("*boostedAuthorId")
                                                                .select("$$accountIdToSuppressions",
                                                                                Relationships.isBlockedOrMutedPath(
                                                                                                "*requestAccountId",
                                                                                                "*boostedAuthorId",
                                                                                                false))
                                                                .out("*isBoostingBlockedOrMuted")
                                                                .keepTrue(new Expr(Ops.NOT,
                                                                                "*isBoostingBlockedOrMuted"))
                                                                .select("$$accountIdToSuppressions",
                                                                                Path.key("*boostedAuthorId", "blocked")
                                                                                                .view(Ops.CONTAINS,
                                                                                                                "*requestAccountId")
                                                                                                .filterPred(Ops.NOT)))

                                // get the poll data
                                .ifTrue(new Expr(ApolloHelpers::statusResultHasPoll, "*statusResult"),
                                                Block.each(ApolloHelpers::origAuthorId, "*authorId", "*status")
                                                                .out("*origAuthorId")
                                                                .each(ApolloHelpers::origStatusId, "*statusId",
                                                                                "*status")
                                                                .out("*origStatusId")
                                                                .hashPartition("*origAuthorId")
                                                                .localSelect("$$pollVotes",
                                                                                Path.key("*origStatusId")
                                                                                                .subselect(
                                                                                                                Path.multiPath(
                                                                                                                                Path.subselect(Path
                                                                                                                                                .key("choices")
                                                                                                                                                .all()
                                                                                                                                                .collectOne(Path.first())
                                                                                                                                                .last()
                                                                                                                                                .view(Ops.SIZE)),
                                                                                                                                Path.key("allVoters")
                                                                                                                                                .multiPath(
                                                                                                                                                                Path.view(Ops.SIZE),
                                                                                                                                                                Path.ifPath(Path
                                                                                                                                                                                .putCollected(
                                                                                                                                                                                                "*requestAccountId")
                                                                                                                                                                                .isCollected((List l) -> l
                                                                                                                                                                                                .get(0) != null),
                                                                                                                                                                                Path.key(
                                                                                                                                                                                                "*requestAccountId"))))))
                                                                .out("*pollData"),
                                                Block.each(Ops.IDENTITY, null).out("*pollData"))

                                .each(ApolloHelpers::getStatusResultText, "*statusResult").out("*text")
                                .each(ApolloHelpers::getStatusResultVisibility, "*statusResult").out("*visibility")
                                // stop if requester is not allowed to see the status
                                .ifTrue(new Expr(Ops.NOT_EQUAL, "*requestAccountId", "*authorId"),
                                                // if the request account id is null, only return public/unlisted
                                                // statuses
                                                Block.ifTrue(new Expr(Ops.IS_NULL, "*requestAccountId"),
                                                                Block.keepTrue(
                                                                                new Expr(Ops.OR, new Expr(Ops.EQUAL,
                                                                                                "*visibility",
                                                                                                StatusVisibility.Public),
                                                                                                new Expr(Ops.EQUAL,
                                                                                                                "*visibility",
                                                                                                                StatusVisibility.Unlisted))),
                                                                // otherwise, return private statuses if they are a
                                                                // follower
                                                                Block.ifTrue(new Expr(Ops.EQUAL, "*visibility",
                                                                                StatusVisibility.Private),
                                                                                Block.select("$$followerToFollowees",
                                                                                                Path.key("*requestAccountId",
                                                                                                                "*authorId"))
                                                                                                .out("*followee")
                                                                                                .keepTrue(new Expr(
                                                                                                                Ops.IS_NOT_NULL,
                                                                                                                "*followee")),
                                                                                // return direct statuses if they are
                                                                                // mentioned
                                                                                Block.ifTrue(new Expr(Ops.EQUAL,
                                                                                                "*visibility",
                                                                                                StatusVisibility.Direct),
                                                                                                Block.select("$$accountIdToAccount",
                                                                                                                Path.key("*requestAccountId")
                                                                                                                                .filterPred(Ops.IS_NOT_NULL))
                                                                                                                .out("*requestAccount")
                                                                                                                .macro(ApolloHelpers
                                                                                                                                .extractFields("*requestAccount",
                                                                                                                                                "*name"))
                                                                                                                .each(Token::parseTokens,
                                                                                                                                "*text")
                                                                                                                .out("*tokens")
                                                                                                                .each(Token::filterMentions,
                                                                                                                                "*tokens")
                                                                                                                .out("*mentions")
                                                                                                                .each((Set<String> mentions,
                                                                                                                                String accountName) -> mentions
                                                                                                                                                .contains(accountName),
                                                                                                                                "*mentions",
                                                                                                                                "*name")
                                                                                                                .out("*isMentioned")
                                                                                                                .keepTrue("*isMentioned")))))

                                // create query result
                                .each((Long statusId, StatusResult statusResult, List pollData,
                                                Status originalStatus) -> {
                                        if (pollData != null) {
                                                PollInfo p = new PollInfo();
                                                List<List> votes = (List) pollData.get(0);
                                                Map votesm = new HashMap();
                                                for (List l : votes)
                                                        votesm.put(l.get(0), l.get(1));
                                                p.setVoteCounts(votesm);
                                                p.setTotalVoters((int) pollData.get(1));
                                                if (pollData.size() > 2 && pollData.get(2) != null)
                                                        p.setOwnVotes((Set) pollData.get(2));
                                                else
                                                        p.setOwnVotes(new HashSet());
                                                statusResult.setPollInfo(p);
                                        }
                                        if (originalStatus != null) {
                                                statusResult.setEditTimestamp(statusResult.timestamp);
                                                statusResult.setTimestamp(originalStatus.timestamp);
                                        }
                                        return new StatusResultWithId(statusId, statusResult);
                                }, "*statusId", "*statusResult", "*pollData", "*originalStatus")
                                .out("*statusQueryResult")
                                .each((RamaFunction2<Integer, StatusResultWithId, IndexedStatusResultWithId>) IndexedStatusResultWithId::new,
                                                "*index", "*statusQueryResult")
                                .out("*indexedStatusResultWithId")

                                .originPartition()
                                .agg(Agg.list("*indexedStatusResultWithId")).out("*unsortedResults")
                                // get the author ids
                                .each((List<IndexedStatusResultWithId> unsortedResults) -> unsortedResults.stream()
                                                .flatMap(o -> ApolloHelpers.getAuthorIds(o.statusResultWithId.status)
                                                                .stream())
                                                .collect(Collectors.toSet()),
                                                "*unsortedResults")
                                .out("*authorIds")
                                // get the account metadata
                                // this could have been queried individually for each status in
                                // `resolveStatusResult`,
                                // but as a perf optimization we are querying it here to avoid wasteful
                                // duplicate queries.
                                .invokeQuery("getAccountIdToMetadata", "*requestAccountId", "*authorIds")
                                .out("*accountIdToMetadata")
                                // sort the results and update the account metadata
                                .each((List<IndexedStatusResultWithId> unsortedResults,
                                                Map<Long, AccountMetadata> accountIdToMetadata) -> {
                                        ArrayList<IndexedStatusResultWithId> results = new ArrayList<>(unsortedResults);
                                        results.sort(
                                                        (IndexedStatusResultWithId a,
                                                                        IndexedStatusResultWithId b) -> (int) (a.index
                                                                                        - b.index));
                                        for (IndexedStatusResultWithId res : results) {
                                                ApolloHelpers.updateAccountMetadata(res.statusResultWithId.status,
                                                                accountIdToMetadata);
                                        }
                                        return results.stream().map(o -> o.statusResultWithId)
                                                        .collect(Collectors.toList());
                                }, "*unsortedResults", "*accountIdToMetadata").out("*sortedResults")
                                // get the mentioned accounts
                                .invokeQuery("getAccountsFromMentions", "*requestAccountId", "*sortedResults")
                                .out("*mentionedAccounts")
                                // get the parent accounts for replies
                                .each(ApolloHelpers::getParentAccountIdsFromStatusResults, "*sortedResults")
                                .out("*parentAccountIds")
                                .invokeQuery("getAccountsFromAccountIds", "*requestAccountId", "*parentAccountIds")
                                .out("*parentAccounts")
                                // create the final object
                                .each((List<StatusResultWithId> sortedResults, List<AccountWithId> mentionedAccounts,
                                                List<AccountWithId> parentAccounts) -> {
                                        Map<String, AccountWithId> mentions = new HashMap<>();
                                        for (AccountWithId accountWithId : mentionedAccounts)
                                                mentions.put(accountWithId.account.name, accountWithId);
                                        for (AccountWithId accountWithId : parentAccounts)
                                                mentions.put(accountWithId.account.name, accountWithId);
                                        return new StatusQueryResults(sortedResults, mentions, false, false);
                                }, "*sortedResults", "*mentionedAccounts", "*parentAccounts").out("*results");

                topologies.query("getApplicationFromClientId", "*client_id").out("*result")
                                .hashPartition("*client_id")
                                .localSelect("$$clientIdToApplication", Path.key("*client_id"))
                                .out("*application")
                                .ifTrue(new Expr(Ops.IS_NULL, "*application"),
                                                Block.each(() -> null).out("*result"),
                                                Block.each(Ops.IDENTITY, "*application").out("*result"))
                                .originPartition();

                topologies.query("getAllUserIds").out("*result")
                                .allPartition()
                                .localSelect("$$accountIdToAccount", Path.mapKeys())
                                .out("*ids")
                                .originPartition()
                                .agg(Agg.list("*ids")).out("*result");

                topologies.query("getReportFromReportId", "*id").out("*result")
                                .hashPartition("*id")
                                .localSelect("$$reportIdToReport", Path.key("*id")).out("*result")
                                .originPartition();

                topologies.query("getAllReports").out("*result")
                                .allPartition()
                                .localSelect("$$reportIdToReport", Path.mapVals())
                                .out("*reports")
                                .originPartition()
                                .agg(Agg.list("*reports")).out("*result");

                topologies.query("getActiveUsersCount", "*timestamp").out("*result")
                                .allPartition()
                                .localSelect("$$accountIdToTimestamp",
                                                Path.mapVals().filterGreaterThan("*timestamp"))
                                .out("*timestamps")
                                .originPartition()
                                .agg(Agg.list("*timestamps")).out("*list")
                                .each(Ops.IDENTITY, new Expr(Ops.SIZE, "*list"))
                                .out("*result");

                topologies.query("getSpaceFromSpaceId", "*id").out("*space")
                                .hashPartition("*id")
                                .localSelect("$$spaceIdToSpace", Path.key("*id"))
                                .out("*space")
                                .originPartition();

                topologies.query("getAllSpaces").out("*result")
                                .allPartition()
                                .localSelect("$$spaceIdToSpace", Path.mapVals())
                                .out("*spaces")
                                .originPartition()
                                .agg(Agg.list("*spaces")).out("*result");

        }

        public static class StatusDepotExtractor implements RamaFunction1<TBase, Long> {
                @Override
                public Long invoke(TBase o) {
                        if (o instanceof BoostStatus)
                                return ((BoostStatus) o).accountId;
                        else if (o instanceof RemoveBoostStatus)
                                return ((RemoveBoostStatus) o).accountId;
                        else if (o instanceof AddStatus)
                                return ((AddStatus) o).status.authorId;
                        else if (o instanceof EditStatus)
                                return ((EditStatus) o).status.authorId;
                        else if (o instanceof RemoveStatus)
                                return ((RemoveStatus) o).accountId;
                        else
                                throw new RuntimeException("Unexpected type " + o.getClass());
                }
        }

        public static class AccountDepotExtractor implements RamaFunction1<TBase, String> {
                @Override
                public String invoke(TBase o) {
                        if (o instanceof Account)
                                return ((Account) o).name;
                        else if (o instanceof RemoveAccount)
                                return ((RemoveAccount) o).name;
                        else
                                throw new RuntimeException("Unexpected type " + o.getClass());
                }
        }

        public static class ScheduledStatusDepotExtractor implements RamaFunction1<TBase, Long> {
                @Override
                public Long invoke(TBase o) {
                        if (o instanceof AddScheduledStatus)
                                return ((AddScheduledStatus) o).status.authorId;
                        else if (o instanceof EditStatus)
                                return ((EditStatus) o).status.authorId;
                        else if (o instanceof RemoveStatus)
                                return ((RemoveStatus) o).accountId;
                        else if (o instanceof EditScheduledStatusPublishTime)
                                return ((EditScheduledStatusPublishTime) o).accountId;
                        else
                                throw new RuntimeException("Unexpected type " + o.getClass());
                }
        }

        @Override
        public void define(Setup setup, Topologies topologies) {

                setup.declareDepot("*applicationDepot", Depot.hashBy(ApolloHelpers.ExtractClientId.class));
                setup.declareDepot("*userActivityDepot", Depot.hashBy(ApolloHelpers.ExtractAccountId.class));
                setup.declareDepot("*spaceDepot", Depot.hashBy(ApolloHelpers.ExtractId.class));
                setup.declareDepot("*reportDepot", Depot.hashBy(ApolloHelpers.ExtractReportId.class));
                setup.declareDepot("*statusDepot", Depot.hashBy(StatusDepotExtractor.class));
                setup.declareDepot("*scheduledStatusDepot", Depot.hashBy(ScheduledStatusDepotExtractor.class));
                setup.declareDepot("*statusWithIdDepot", Depot.disallow());
                setup.declareDepot("*statusAttachmentWithIdDepot", Depot.hashBy(ApolloHelpers.ExtractUuid.class));
                setup.declareDepot("*accountDepot", Depot.hashBy(AccountDepotExtractor.class));
                setup.declareDepot("*accountWithIdDepot", Depot.disallow());
                setup.declareDepot("*accountEditDepot", Depot.hashBy(ApolloHelpers.ExtractAccountId.class));
                setup.declareDepot("*likeStatusDepot", Depot.hashBy(ApolloHelpers.ExtractAccountId.class));
                setup.declareDepot("*bookmarkStatusDepot", Depot.hashBy(ApolloHelpers.ExtractAccountId.class));
                setup.declareDepot("*muteStatusDepot", Depot.hashBy(ApolloHelpers.ExtractAccountId.class));
                setup.declareDepot("*pinStatusDepot", Depot.hashBy(ApolloHelpers.ExtractAccountId.class));
                setup.declareDepot("*conversationDepot", Depot.hashBy(ApolloHelpers.ExtractAccountId.class));
                setup.declareDepot("*pollVoteDepot", Depot.hashBy(ApolloHelpers.ExtractTargetAuthorId.class));
                setup.declareTickDepot("*scheduledStatusTick", scheduledStatusTickMillis);

                setup.declareObject("*homeTimelines", new HomeTimelines(timelineMaxAmount, enableHomeTimelineRefresh));

                setup.clusterDepot("*followAndBlockAccountDepot", Relationships.class.getName(),
                                "*followAndBlockAccountDepot");

                setup.clusterPState("$$accountIdToSuppressions", Relationships.class.getName(),
                                "$$accountIdToSuppressions");
                setup.clusterPState("$$followerToFollowees", Relationships.class.getName(), "$$followerToFollowees");
                setup.clusterPState("$$followerToFolloweesById", Relationships.class.getName(),
                                "$$followerToFolloweesById");
                setup.clusterPState("$$followeeToFollowers", Relationships.class.getName(), "$$followeeToFollowers");

                setup.clusterPState("$$partitionedFollowersControl", Relationships.class.getName(),
                                "$$partitionedFollowersControl");
                setup.clusterPState("$$partitionedFollowers", Relationships.class.getName(), "$$partitionedFollowers");

                setup.clusterPState("$$hashtagToFollowers", Relationships.class.getName(), "$$hashtagToFollowers");
                setup.clusterPState("$$spaceIdToFollowers", Relationships.class.getName(), "$$spaceIdToFollowers");
                setup.clusterPState("$$accountIdToFilterIdToFilter", Relationships.class.getName(),
                                "$$accountIdToFilterIdToFilter");

                declareFollowsBloomFiltersTopology(topologies);
                declareMicrobatchTopologies(topologies);
                declareAccountsTopology(topologies);
                declareStatusTopology(topologies);
                declareReportsTopology(topologies);
                declareApplicationTopology(topologies);
                declareActivityTopology(topologies);
                declareSpaceTopology(topologies);
                declareQueries(topologies);

        }

}