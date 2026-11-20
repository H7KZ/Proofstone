package io.github.h7kz.proofstone.agent.bukkit;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Reads an item's persistent data container into the wire form of {@code item.pdc} (T-M11-05). The agent is built
 * against the 1.8.8 API and PDC exists from 1.14, so every type is reached by reflection: where the classes are absent
 * {@link #supported()} is false and {@link #read} answers null, which is "not served", never "empty".
 *
 * <p>Each entry is {@code "namespace:key": {"type": T, "value": V}}. The container stores typed primitives and JSON
 * loses the type, so the type travels beside the value: byte, short, int, long, float, double, string, byte[], int[],
 * long[] and a nested container. Anything else (a list, a container array, a type a plugin registered) is
 * {@code {"type": "unsupported", "value": "<what it was>"}}: reported, never dropped.
 */
final class PersistentData {

    static final String DATA_TYPE = "org.bukkit.persistence.PersistentDataType";
    private static final String CONTAINER = "org.bukkit.persistence.PersistentDataContainer";

    /** The wire type name and the {@code PersistentDataType} constant that reads it, most specific first. */
    private static final String[][] TYPES = {
            {"byte", "BYTE"}, {"short", "SHORT"}, {"int", "INTEGER"}, {"long", "LONG"}, {"float", "FLOAT"},
            {"double", "DOUBLE"}, {"string", "STRING"}, {"byte[]", "BYTE_ARRAY"}, {"int[]", "INTEGER_ARRAY"},
            {"long[]", "LONG_ARRAY"}, {"container", "TAG_CONTAINER"}};

    private static final Api API = Api.load(PersistentData.class.getClassLoader(), CONTAINER, DATA_TYPE);

    private PersistentData() {
    }

    static boolean supported() {
        return API != null;
    }

    /** As {@link #read(Object, Supplier)} for a meta with no better name. */
    static Map<String, Object> read(Object meta) {
        return read(meta, () -> "an item");
    }

    /**
     * The meta's PDC, or null when the server has no PDC or the container is empty (an empty container adds nothing, so
     * an item without data reads the same as before). A failure is rethrown naming {@code what}, so a bad item in a long
     * inventory is found; the call still fails, never skips the item.
     */
    static Map<String, Object> read(Object meta, Supplier<String> what) {
        if (API == null || meta == null) {
            return null;
        }
        try {
            Map<String, Object> out = API.read(API.container(meta));
            return out.isEmpty() ? null : out;
        } catch (RuntimeException e) {
            throw new IllegalStateException("could not read the persistent data of " + what.get() + ": " + e.getMessage(), e);
        }
    }

    /** The reflective handles, resolved once. Anything missing means no PDC on this server, never an exception. */
    static final class Api {
        private final Method getKeys;
        private final Method has;
        private final Method get;
        private final Map<String, Object> constants = new LinkedHashMap<>();
        private final Map<Class<?>, Method> accessors = new ConcurrentHashMap<>();

        private Api(Class<?> container, Class<?> dataType) throws ReflectiveOperationException {
            this.getKeys = container.getMethod("getKeys");
            Class<?> key = Class.forName("org.bukkit.NamespacedKey", false, container.getClassLoader());
            this.has = container.getMethod("has", key, dataType);
            this.get = container.getMethod("get", key, dataType);
            for (String[] type : TYPES) {
                Object constant = null;
                try {
                    constant = dataType.getField(type[1]).get(null);
                } catch (NoSuchFieldException e) {
                    // Only the nested container may be absent (its values then read as unsupported); a missing primitive
                    // means the API is not the one the agent knows.
                    if (!"container".equals(type[0])) {
                        throw e;
                    }
                }
                constants.put(type[0], constant);
            }
        }

        /**
         * The handles, or null when this server's PDC API is absent or not the shape the agent reads (a missing class,
         * method or primitive constant). Never throws: it runs in a static initialiser, where a throw would be an
         * ExceptionInInitializerError in the agent's onEnable.
         */
        static Api load(ClassLoader loader, String containerName, String dataTypeName) {
            try {
                return new Api(Class.forName(containerName, false, loader), Class.forName(dataTypeName, false, loader));
            } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
                return null;
            }
        }

        Object container(Object meta) {
            Method accessor = accessors.computeIfAbsent(meta.getClass(), type -> {
                Method found = publicMethod(type, "getPersistentDataContainer");
                if (found == null) {
                    throw new IllegalStateException(type.getName() + " has no public getPersistentDataContainer");
                }
                return found;
            });
            return call(accessor, meta);
        }

        /**
         * The method as declared by a public class or interface. The server's meta class (CraftMetaItem) is package-private,
         * so invoking the method found on it is an IllegalAccessException; the same method on ItemMeta is callable.
         */
        private static Method publicMethod(Class<?> type, String name) {
            List<Class<?>> candidates = new ArrayList<>();
            for (Class<?> c = type; c != null; c = c.getSuperclass()) {
                candidates.add(c);
                candidates.addAll(Arrays.asList(c.getInterfaces()));
            }
            for (Class<?> c : candidates) {
                if (java.lang.reflect.Modifier.isPublic(c.getModifiers())) {
                    try {
                        return c.getMethod(name);
                    } catch (NoSuchMethodException e) {
                        // Not declared here: try the next candidate.
                    }
                }
            }
            return null;
        }

        Map<String, Object> read(Object pdc) {
            Set<?> keys = (Set<?>) call(getKeys, pdc);
            // Sorted, so the reply for one container is stable (a Set has no order).
            Map<String, Object> out = new TreeMap<>();
            for (Object key : keys) {
                out.put(key.toString(), entry(pdc, key));
            }
            return out;
        }

        private Map<String, Object> entry(Object pdc, Object key) {
            for (String[] type : TYPES) {
                Object constant = constants.get(type[0]);
                if (constant == null || !Boolean.TRUE.equals(call(has, pdc, key, constant))) {
                    continue;
                }
                Object value = call(get, pdc, key, constant);
                Map<String, Object> out = new LinkedHashMap<>();
                out.put("type", type[0]);
                out.put("value", "container".equals(type[0]) ? read(value) : wire(value));
                return out;
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("type", "unsupported");
            out.put("value", "no readable primitive type for " + key);
            return out;
        }

        /** Arrays become lists: the wire is JSON. */
        private static Object wire(Object value) {
            List<Object> list = new ArrayList<>();
            if (value instanceof byte[]) {
                for (byte b : (byte[]) value) {
                    list.add((int) b);
                }
            } else if (value instanceof int[]) {
                for (int i : (int[]) value) {
                    list.add(i);
                }
            } else if (value instanceof long[]) {
                for (long l : (long[]) value) {
                    list.add(l);
                }
            } else {
                return value;
            }
            return Collections.unmodifiableList(list);
        }

        private static Object call(Method method, Object target, Object... args) {
            try {
                return method.invoke(target, args);
            } catch (InvocationTargetException e) {
                throw new IllegalStateException("PDC read failed: " + e.getCause(), e.getCause());
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("PDC read failed: " + e, e);
            }
        }
    }
}
