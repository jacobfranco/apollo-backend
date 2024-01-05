package com.apollo.backend.ops;

import com.rpl.rama.ops.RamaFunction1;

import java.lang.reflect.Field;

public class ExtractField implements RamaFunction1 {

    private String fieldName;

    public ExtractField(String name) {
        this.fieldName = name;
    }

    public Object extractFieldValue(Object obj) {
        try {
            Field field = obj.getClass().getDeclaredField(fieldName);
            field.setAccessible(true); // Allows access to private fields
            return field.get(obj);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            e.printStackTrace();
            return null;
        }
    }
    
}
