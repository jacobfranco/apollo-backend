package com.apollo.backend.serialization;

import com.apollo.backend.data.*;
import org.apache.thrift.TBase;
import java.util.*;

public class ApolloSerialization extends ThriftSerialization<TBase<?, ?>> {

    // TODO: Add the rest / make sure that this actually works still
    @Override
    protected Map<Integer, Class<? extends TBase<?, ?>>> typeIds() {
        Map<Integer, Class<? extends TBase<?, ?>>> ret = new HashMap<>();
        List<Class<? extends TBase<?, ?>>> classes = Arrays.asList(
            (Class<? extends TBase<?, ?>>) AcceptFollowRequest.class,
            (Class<? extends TBase<?, ?>>) Account.class,
            (Class<? extends TBase<?, ?>>) AccountMetadata.class,
            (Class<? extends TBase<?, ?>>) AccountRelationshipQueryResult.class,
            (Class<? extends TBase<?, ?>>) AccountWithId.class,
            (Class<? extends TBase<?, ?>>) AddAuthCode.class,
            (Class<? extends TBase<?, ?>>) AddFilter.class,
            (Class<? extends TBase<?, ?>>) AddScheduledStatus.class,
            (Class<? extends TBase<?, ?>>) AddStatus.class,
            (Class<? extends TBase<?, ?>>) AddStatusToFilter.class,
            (Class<? extends TBase<?, ?>>) Attachment.class,
            (Class<? extends TBase<?, ?>>) AttachmentWithId.class,
            (Class<? extends TBase<?, ?>>) BlockAccount.class,
            (Class<? extends TBase<?, ?>>) BookmarkStatus.class,
            (Class<? extends TBase<?, ?>>) BoostStatus.class,
            (Class<? extends TBase<?, ?>>) BoostStatusContent.class,
            (Class<? extends TBase<?, ?>>) Conversation.class,
            (Class<? extends TBase<?, ?>>) EditAccount.class,
            (Class<? extends TBase<?, ?>>) EditAccountField.class,
            (Class<? extends TBase<?, ?>>) EditConversation.class,
            (Class<? extends TBase<?, ?>>) EditScheduledStatusPublishTime.class,
            (Class<? extends TBase<?, ?>>) EditStatus.class,
            (Class<? extends TBase<?, ?>>) FeatureAccount.class,
            (Class<? extends TBase<?, ?>>) FeaturedHashtagInfo.class,
            (Class<? extends TBase<?, ?>>) FeatureHashtag.class,
            (Class<? extends TBase<?, ?>>) Filter.class,
            (Class<? extends TBase<?, ?>>) FilterWithId.class,
            (Class<? extends TBase<?, ?>>) FollowAccount.class,
            (Class<? extends TBase<?, ?>>) FollowHashtag.class,
            (Class<? extends TBase<?, ?>>) Follower.class,
            (Class<? extends TBase<?, ?>>) FollowerFanout.class,
            (Class<? extends TBase<?, ?>>) FollowLockedAccount.class,
            (Class<? extends TBase<?, ?>>) HashtagFanout.class,
            (Class<? extends TBase<?, ?>>) IndexedAccountWithId.class,
            (Class<? extends TBase<?, ?>>) IndexedStatusResultWithId.class,
            (Class<? extends TBase<?, ?>>) ItemStats.class,
            (Class<? extends TBase<?, ?>>) KeyValuePair.class,
            (Class<? extends TBase<?, ?>>) LikeStatus.class,
            (Class<? extends TBase<?, ?>>) Marker.class,
            (Class<? extends TBase<?, ?>>) MuteAccount.class,
            (Class<? extends TBase<?, ?>>) MuteAccountOptions.class,
            (Class<? extends TBase<?, ?>>) MuteStatus.class,
            (Class<? extends TBase<?, ?>>) NormalStatusContent.class,
            (Class<? extends TBase<?, ?>>) Note.class,
            (Class<? extends TBase<?, ?>>) Notification.class,
            (Class<? extends TBase<?, ?>>) NotificationContent.class,
            (Class<? extends TBase<?, ?>>) PinStatus.class,
            (Class<? extends TBase<?, ?>>) PollContent.class,
            (Class<? extends TBase<?, ?>>) PollInfo.class,
            (Class<? extends TBase<?, ?>>) ProfileSearchRecord.class,
            (Class<? extends TBase<?, ?>>) QueryFilterOptions.class,
            (Class<? extends TBase<?, ?>>) RejectFollowRequest.class,
            (Class<? extends TBase<?, ?>>) RemoveAuthCode.class,
            (Class<? extends TBase<?, ?>>) RemoveBlockAccount.class,
            (Class<? extends TBase<?, ?>>) RemoveBookmarkStatus.class,
            (Class<? extends TBase<?, ?>>) RemoveBoostStatus.class,
            (Class<? extends TBase<?, ?>>) RemoveConversation.class,
            (Class<? extends TBase<?, ?>>) RemoveFeatureAccount.class,
            (Class<? extends TBase<?, ?>>) RemoveFeatureHashtag.class,
            (Class<? extends TBase<?, ?>>) RemoveFilter.class,
            (Class<? extends TBase<?, ?>>) RemoveFollowAccount.class,
            (Class<? extends TBase<?, ?>>) RemoveFollowHashtag.class,
            (Class<? extends TBase<?, ?>>) RemoveLikeStatus.class,
            (Class<? extends TBase<?, ?>>) RemoveMuteAccount.class,
            (Class<? extends TBase<?, ?>>) RemoveMuteStatus.class,
            (Class<? extends TBase<?, ?>>) RemovePinStatus.class,
            (Class<? extends TBase<?, ?>>) RemoveStatus.class,
            (Class<? extends TBase<?, ?>>) RemoveStatusFromFilter.class,
            (Class<? extends TBase<?, ?>>) RemoveStatusWithId.class,
            (Class<? extends TBase<?, ?>>) ReplyStatusContent.class,
            (Class<? extends TBase<?, ?>>) Series.class,
            (Class<? extends TBase<?, ?>>) Status.class,
            (Class<? extends TBase<?, ?>>) StatusContent.class,
            (Class<? extends TBase<?, ?>>) StatusMetadata.class,
            (Class<? extends TBase<?, ?>>) StatusPointer.class,
            (Class<? extends TBase<?, ?>>) StatusQueryResults.class,
            (Class<? extends TBase<?, ?>>) StatusResponseNotificationContent.class,
            (Class<? extends TBase<?, ?>>) StatusResult.class,
            (Class<? extends TBase<?, ?>>) StatusResultContent.class,
            (Class<? extends TBase<?, ?>>) StatusResultWithId.class,
            (Class<? extends TBase<?, ?>>) StatusWithId.class,
            (Class<? extends TBase<?, ?>>) StatusSearchRecord.class,
            (Class<? extends TBase<?, ?>>) StatusWithId.class,
            (Class<? extends TBase<?, ?>>) UpdateKeyword.class
        );
        for (int i = 0; i < classes.size(); i++) {
            ret.put(i, classes.get(i));
        }
        return ret;
    }
}
