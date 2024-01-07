package com.apollo.backend.serialization;

import com.apollo.backend.data.*;
import org.apache.thrift.TBase;
import java.util.*;

public class ApolloSerialization extends ThriftSerialization<TBase<?, ?>> {

    // TODO: Fix 
    @Override
    protected Map<Integer, Class<? extends TBase<?, ?>>> typeIds() {
        Map<Integer, Class<? extends TBase<?, ?>>> ret = new HashMap<>();
        Class<? extends TBase<?, ?>>[] classes = new Class[]{
            Account.class,       
            AccountMetadata.class, 
            AccountWithId.class, 
            AddAuthCode.class,
            Attachment.class,
            AttachmentKind.class,
            AttachmentWithId.class,
        };
        for (int i = 0; i < classes.length; i++) ret.put(i, classes[i]);
        return ret;
    }
}
