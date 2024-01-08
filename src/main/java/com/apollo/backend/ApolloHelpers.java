package com.apollo.backend;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.lang.reflect.*;

import org.apache.thrift.TBase;
import org.apache.thrift.TFieldIdEnum;
import org.apache.thrift.TUnion;

import com.apollo.backend.ops.*;
import com.rpl.rama.*;

public class ApolloHelpers {

   public static final ConcurrentHashMap<Class<?>, Map<String, TFieldIdEnum>> TFIELD_CACHE = new ConcurrentHashMap<>();

    public static class ExtractCode extends ExtractField {
        public ExtractCode() { super("code"); }
      }

      public static class ExtractName extends ExtractField {
        public ExtractName() { super("name"); }
      }

       public static Block extractFields(Object from, String... fieldVars) {
    Block.Impl ret = Block.create();
    for(String f: fieldVars) {
      String name;
      if(Helpers.isGeneratedVar(f)) name = Helpers.getGeneratedVarPrefix(f);
      else name = f.substring(1);
      ret = ret.each(new ExtractField(name), from).out(f);
    }
    return ret;
  }

  public static Object getTFieldByName(TBase<?,?> obj, String fieldName) {
    TFieldIdEnum field = getTFieldCache(obj.getClass()).get(fieldName);
    if (field == null) {
        throw new RuntimeException("Field " + fieldName + " does not exist on " + obj.getClass());
    }

    Object ret = null;
    // Use Reflection to bypass generic type issues
    try {
        Method isSetMethod = obj.getClass().getMethod("isSet", field.getClass());
        Method getFieldValueMethod = obj.getClass().getMethod("getFieldValue", field.getClass());
        
        if ((Boolean) isSetMethod.invoke(obj, field)) {
            ret = getFieldValueMethod.invoke(obj, field);
        }
    } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
        throw new RuntimeException("Reflection error: " + e.getMessage());
    }

    if (ret instanceof TUnion) {
        ret = ((TUnion<?, ?>) ret).getFieldValue();
    }
    return ret;
}

public static Map<String, TFieldIdEnum> getTFieldCache(Class<?> thriftClass) {
    Map<String, TFieldIdEnum> ret = TFIELD_CACHE.get(thriftClass);
    if (ret == null) {
        try {
            Field f = thriftClass.getField("metaDataMap");
            // Correctly casting the map with checked type safety
            Object rawData = f.get(thriftClass);
            if (!(rawData instanceof Map)) {
                throw new ClassCastException("Field 'metaDataMap' is not a Map");
            }
            Map<?, ?> rawMap = (Map<?, ?>) rawData;

            ret = new HashMap<>();
            for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
                if (!(entry.getKey() instanceof TFieldIdEnum)) {
                    throw new ClassCastException("Incompatible key type in map");
                }
                ret.put(((TFieldIdEnum) entry.getKey()).getFieldName(), (TFieldIdEnum) entry.getKey());
            }
            TFIELD_CACHE.put(thriftClass, ret);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    return ret;
}


      
    
}
