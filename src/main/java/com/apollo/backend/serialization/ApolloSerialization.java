package com.apollo.backend.serialization;

import com.apollo.backend.data.*;
import java.util.*;
// Test
public class ApolloSerialization extends ThriftSerialization {

    @Override
    protected Map<Integer, Class> typeIds() {
        Map<Integer, Class> ret = new HashMap<>();
        Class[] classes = {
            Account.class,
            AccountMetadata.class,
            AccountWithId.class,
            AddAuthCode.class
        };
        for (int i = 0; i < classes.length; i++) ret.put(i, classes[i]);
    return ret;
    }
    
}
