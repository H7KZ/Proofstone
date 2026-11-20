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

    private static final Api API = Api.load();

    private PersistentData() {
    }

    static boolean supported() {
        return API != null;
    }

    /**
     * The meta's PDC, or null when the server has no PDC or the container is empty (an empty container adds nothing, so
     * an item without data reads the same as before).
     */
    static Map<String, Object> read(Object meta) {
        if (API == null || meta == null) {
            return null;
        }
        Map<String, Object> out = API.read(API.container(meta));
        return out.isEmpty() ? null : out;
    }

    /** The reflective handles, resolved once. A class that cannot be loaded at all means no PDC on this server. */
    static final class Api {
        private final Method getKeys;
        private final Method has;
        private final Method get;
        private final Map<String, Object> constants = new LinkedHashMap<>();
        private final Map<Class<?>, Method> accessors = new ConcurrentHashMap<>();

        private Api(Class<?> container, Class<?> dataType) throws NoSuchMethodException, ClassNotFoundException {
            this.getKeys = container.getMethod("getKeys");
            Class<?> key = Class.forName("org.bukkit.NamespacedKey", false, container.getClassLoader());
            this.has = container.getMethod("has", key, dataType);
            this.get = container.getMethod("get", key, dataType);
            for (String[] type : TYPES) {
                try {
                    constants.put(type[0], dataType.getField(type[1]).get(null));
                } catch (NoSuchFieldException | IllegalAccessException e) {
                    // The constant is newer than this server: its values fall to "unsupported", never a guess.
                    constants.put(type[0], null);
                }
            }
        }

        static Api load() {
            try {
                ClassLoader loader = PersistentData.class.getClassLoader();
                return new Api(Class.forName(CONTAINER, false, loader), Class.forName(DATA_TYPE, false, loader));
            } catch (ClassNotFoundException e) {
                return null;
            } catch (NoSuchMethodException e) {
                throw new IllegalStateException("this server's PersistentDataContainer has an unknown shape", e);
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
