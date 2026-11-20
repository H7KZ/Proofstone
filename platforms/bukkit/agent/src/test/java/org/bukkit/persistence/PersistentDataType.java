package org.bukkit.persistence;

/** Test stand-in with the real constant names (Bukkit 1.14+), matching a stored value by its Java class. */
public final class PersistentDataType {
    public static final PersistentDataType BYTE = new PersistentDataType(Byte.class);
    public static final PersistentDataType SHORT = new PersistentDataType(Short.class);
    public static final PersistentDataType INTEGER = new PersistentDataType(Integer.class);
    public static final PersistentDataType LONG = new PersistentDataType(Long.class);
    public static final PersistentDataType FLOAT = new PersistentDataType(Float.class);
    public static final PersistentDataType DOUBLE = new PersistentDataType(Double.class);
    public static final PersistentDataType STRING = new PersistentDataType(String.class);
    public static final PersistentDataType BYTE_ARRAY = new PersistentDataType(byte[].class);
    public static final PersistentDataType INTEGER_ARRAY = new PersistentDataType(int[].class);
    public static final PersistentDataType LONG_ARRAY = new PersistentDataType(long[].class);
    public static final PersistentDataType TAG_CONTAINER = new PersistentDataType(PersistentDataContainer.class);

    private final Class<?> primitive;

    private PersistentDataType(Class<?> primitive) {
        this.primitive = primitive;
    }

    public boolean matches(Object value) {
        return primitive.isInstance(value);
    }
}
