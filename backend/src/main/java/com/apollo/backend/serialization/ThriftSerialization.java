package com.apollo.backend.serialization;

import org.apache.thrift.*;
import org.apache.thrift.protocol.TCompactProtocol;
import java.io.*;
import java.util.*;
import java.lang.reflect.InvocationTargetException;
import com.rpl.rama.RamaCustomSerialization;

// TODO: Make sure this works
public abstract class ThriftSerialization<T extends TBase<?, ?>> implements RamaCustomSerialization<T> {
    private final Map<Byte, Class<? extends T>> _idToType = new HashMap<>();
    private final Map<Class<? extends T>, Byte> _typeToId = new HashMap<>();

    protected ThriftSerialization() {
        Map<Integer, Class<? extends T>> m = typeIds();
        for (Map.Entry<Integer, Class<? extends T>> entry : m.entrySet()) {
            Byte id = entry.getKey().byteValue();
            Class<? extends T> clazz = entry.getValue();
            _idToType.put(id, clazz);
            _typeToId.put(clazz, id);
        }
    }

    @Override
    public void serialize(T obj, DataOutput out) throws Exception {
        Byte id = _typeToId.get(obj.getClass());
        if (id == null) throw new RuntimeException("Could not find type id for " + obj.getClass());
        out.writeByte(id);
        byte[] serialized = new TSerializer(new TCompactProtocol.Factory()).serialize(obj);
        out.writeInt(serialized.length);
        out.write(serialized);
    }

    @Override
    public T deserialize(DataInput in) throws Exception {
        Class<? extends T> clazz = _idToType.get(in.readByte());
        if (clazz == null) {
            throw new RuntimeException("Could not find class for given type id");
        }
        T obj;
        try {
            obj = clazz.getDeclaredConstructor().newInstance();
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            throw new RuntimeException("Error instantiating class " + clazz.getName(), e);
        }
        byte[] arr = new byte[in.readInt()];
        in.readFully(arr);
        new TDeserializer(new TCompactProtocol.Factory()).deserialize(obj, arr);
        return obj;
    }

    @Override
    @SuppressWarnings("rawtypes")
    public Class<TBase> targetType() {
        return TBase.class;
    }

    protected abstract Map<Integer, Class<? extends T>> typeIds();
}
