package com.apollo.backend.serialization;

import com.apollo.backend.data.*;
import org.apache.thrift.TBase;
import java.util.*;

public class ApolloSerialization extends ThriftSerialization<TBase<?, ?>> {

    @Override
    protected Map<Integer, Class<? extends TBase<?, ?>>> typeIds() {
        Map<Integer, Class<? extends TBase<?, ?>>> ret = new HashMap<>();
        List<Class<? extends TBase<?, ?>>> classes = Arrays.asList(
            (Class<? extends TBase<?, ?>>) Account.class,
            (Class<? extends TBase<?, ?>>) AccountMetadata.class,
            (Class<? extends TBase<?, ?>>) AccountWithId.class,
            (Class<? extends TBase<?, ?>>) AddAuthCode.class,
            (Class<? extends TBase<?, ?>>) Attachment.class,
            (Class<? extends TBase<?, ?>>) AttachmentWithId.class
        );
        for (int i = 0; i < classes.size(); i++) {
            ret.put(i, classes.get(i));
        }
        return ret;
    }
}
