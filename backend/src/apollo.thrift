namespace java com.apollo.backend.data

typedef i64 AccountId
typedef i64 StatusId
typedef i64 Timestamp
typedef i64 FilterId

enum FilterContext {
  Home = 1,
  Notifications = 2,
  Public = 3,
  Thread = 4,
  Account = 5
}

enum AttachmentKind {
  Image = 1,
  Video = 2
}

enum StatusVisibility {
  Public = 1,
  Unlisted = 2,
  Private = 3,
  Direct = 4
}

enum FilterAction {
  Warn = 1,
  Hide = 2
}

struct KeyValuePair {
  1: required string key;
  2: required string value;
}

struct Marker {
  1: required string lastReadId; // must store the stringified id because that is how the client must receive it later
  2: required i32 version;
  3: required i64 timestamp;
}

struct Account {
  1: required string name;
  2: required string email;
  3: required string pwdHash;
  4: required string locale;
  5: required string uuid;
  6: required string publicKey; 
  7: required Timestamp timestamp;
  8: optional string displayName;
  9: optional string bio;
  10: optional bool locked;
  11: optional bool bot;
  12: optional bool discoverable;
  13: optional AttachmentWithId header;
  14: optional AttachmentWithId avatar;
  15: optional list<KeyValuePair> fields;
  16: optional map<string, Marker> markers;
  17: optional map<string, string> preferences;
}


struct AccountMetadata {
  1: required i32 statusCount;
  2: required i32 followerCount;
  3: required i32 followeeCount;
  4: required bool isFollowedByRequester;
  5: required bool isFollowingRequester;
  6: optional Timestamp lastStatusTimestamp;
}

struct AccountWithId {
  1: required AccountId accountId;
  2: required Account account;
  3: required AccountMetadata metadata;
}

struct AddAuthCode {
  1: required string code;
  2: required AccountId accountId;
}

struct AttachmentWithId {
  1: required string uuid;
  2: required Attachment attachment;
}

struct Attachment {
  1: required AttachmentKind kind;
  2: required string path;
  3: required string description;
}

struct RemoveAuthCode {
  1: required string code;
}

struct FollowAccount {
  1: required AccountId accountId;
  2: required AccountId targetId;
  3: required Timestamp timestamp;
  4: optional bool showBoosts;
  5: optional bool notify;
  6: optional list<string> languages;
  7: optional string followerSharedInboxUrl;
}

union EditAccountField {
  1: string email;
  2: string pwdHash;
  3: string locale;
  4: string publicKey;
  5: string displayName;
  6: string bio;
  7: bool locked;
  8: bool bot;
  9: bool discoverable;
  10: AttachmentWithId header;
  11: AttachmentWithId avatar;
  12: list<KeyValuePair> fields;
  13: map<string, Marker> markers;
  14: map<string, string> preferences;
}

struct Follower {
  1: required AccountId accountId;
  2: required bool showBoosts;
  3: optional list<string> languages;
  4: optional string sharedInboxUrl; // TODO: Maybe change
}

struct Status {
  1: required AccountId authorId;
  2: required StatusContent content;
  3: required Timestamp timestamp;
  4: optional string remoteUrl;
  5: optional string language;
}


struct LikeStatus {
  1: required AccountId accountId;
  2: required StatusPointer target;
  3: required Timestamp timestamp;
}

struct BoostStatus {
  1: required string uuid;
  2: required AccountId accountId;
  3: required StatusPointer target;
  4: required Timestamp timestamp;
  5: optional string remoteUrl;
}

struct NormalStatusContent {
  1: required string text;
  2: required StatusVisibility visibility;
  3: optional PollContent pollContent;
  4: optional list<AttachmentWithId> attachments;
  5: optional string sensitiveWarning;
}

struct ReplyStatusContent {
  1: required string text;
  2: required StatusVisibility visibility;
  3: required StatusPointer parent;
  4: optional PollContent pollContent;
  5: optional list<AttachmentWithId> attachments;
  6: optional string sensitiveWarning;
}

struct BoostStatusContent {
  1: required StatusPointer boosted;
}

union StatusContent {
  1: NormalStatusContent normal;
  2: ReplyStatusContent reply;
  3: BoostStatusContent boost;
}

struct AddStatus {
  1: required string uuid;
  2: required Status status;
}

struct StatusPointer {
  1: required AccountId authorId;
  2: required StatusId statusId;
  3: optional bool shouldExclude;
}

struct StatusResult {
  1: required AccountWithId author;
  2: required StatusResultContent content;
  3: required StatusMetadata metadata;
  4: required Timestamp timestamp;
  5: optional Timestamp editTimestamp;
  6: optional PollInfo pollInfo; // kept here because normal statuses, replies, and boosts can all have polls
  7: optional string remoteUrl;
}

struct StatusQueryResults {
  1: required list<StatusResultWithId> results;
  2: required map<string, AccountWithId> mentions;
  3: required bool reachedEnd;
  4: required bool refreshed;
  // not necessarily the last one in `results` since it could've been excluded
  5: optional StatusPointer lastStatusPointer;
}

struct StatusResultWithId {
  1: required StatusId statusId;
  2: required StatusResult status;
}

typedef NormalStatusContent NormalStatusResultContent
typedef ReplyStatusContent ReplyStatusResultContent

struct BoostStatusResultContent {
  1: required StatusId statusId;
  2: required StatusResult status;
}

union StatusResultContent {
  1: NormalStatusResultContent normal;
  2: ReplyStatusResultContent reply;
  3: BoostStatusResultContent boost;
}

struct StatusMetadata {
  1: required list<MatchingFilter> filters;
  2: required bool favorited;
  3: required bool boosted;
  4: required bool muted;
  5: required bool bookmarked;
  6: required bool pinned;
  7: required i32 favoriteCount;
  8: required i32 boostCount;
  9: required i32 replyCount;
}

struct PollInfo {
  1: required i32 totalVoters;
  2: required set<i32> ownVotes;
  3: required map<i32, i32> voteCounts;
}

struct PollContent {
  1: required list<string> choices;
  2: required Timestamp expirationMillis;
  3: required bool multipleChoice;
}

struct MatchingFilter {
  1: required FilterId filterId;
  2: required Filter filter;
  3: required list<KeywordFilter> keywordMatches;
  4: required bool statusFilterMatch;
}

struct Filter {
  1: required AccountId accountId;
  2: required string title;
  3: required set<FilterContext> contexts;
  4: required list<KeywordFilter> keywords;
  5: required set<StatusPointer> statuses;
  6: required FilterAction action;
  7: required Timestamp timestamp;
  8: optional i64 expirationMillis;
}

struct KeywordFilter {
  1: required string word;
  2: required bool wholeWord;
}

struct QueryFilterOptions {
  1: required FilterContext filterContext;
  2: required bool excludeBlockedAndMuted;
}
