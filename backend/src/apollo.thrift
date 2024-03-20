namespace java com.apollo.backend.data

typedef i64 AccountId
typedef i64 Timestamp

enum AttachmentKind {
  Image = 1,
  Video = 2
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