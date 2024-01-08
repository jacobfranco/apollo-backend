namespace java com.apollo.backend.data

typedef i64 AccountId
typedef i64 Timestamp

enum AttachmentKind {
  Image = 1,
  Video = 2
}

struct Account {
  1: required string username;
  2: required string email;
  3: required string locale; 
  4: required string pwdHash;
  5: required string uuid;
  6: required string publicKey;
  7: required Timestamp timestamp;
  8: optional string displayName;
  9: optional string bio;
  10: optional AttachmentWithId avatar;

/*  TODO: Fields to consider later
  11: optional bool locked;
  12: optional bool bot;
  13: optional bool discoverable;
  14: optional AttachmentWithId header;
  16: optional list<KeyValuePair> fields;
  17: optional map<string, Marker> markers;
  18: optional map<string, string> preferences;  */
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