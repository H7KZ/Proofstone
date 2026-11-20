package io.github.h7kz.proofstone.agent.bukkit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;

/**
 * The reflective PDC read (T-M11-05) against stand-in Bukkit 1.14 classes: the agent is compiled against 1.8.8, so the
 * real API is never on this classpath. The real server read is conformance C-I01.
 */
class PersistentDataTest {

    /** A public class so reflection can call it, like CraftMetaItem. */
    public static final class Meta {
        private final Container container = new Container();

        public PersistentDataContainer getPersistentDataContainer() {
            return container;
        }
    }

    /** The public face of the meta, like ItemMeta. */
    public interface Holder {
        PersistentDataContainer getPersistentDataContainer();
    }

    /** Package-private like CraftMetaItem: the method found on this class cannot be invoked (found on 1.18.2). */
    static final class HiddenMeta implements Holder {
        @Override
        public PersistentDataContainer getPersistentDataContainer() {
            return new Container().with("p:b", (byte) 1);
        }
    }

    static final class Container implements PersistentDataContainer {
        final Map<String, Object> values = new LinkedHashMap<>();

        Container with(String key, Object value) {
            values.put(key, value);
            return this;
        }

        @Override
        public Set<NamespacedKey> getKeys() {
            Set<NamespacedKey> keys = new LinkedHashSet<>();
            for (String key : values.keySet()) {
                String[] parts = key.split(":");
                keys.add(new NamespacedKey(parts[0], parts[1]));
            }
            return keys;
        }

        @Override
        public boolean has(NamespacedKey key, PersistentDataType type) {
            return values.containsKey(key.toString()) && type.matches(values.get(key.toString()));
        }

        @Override
        public Object get(NamespacedKey key, PersistentDataType type) {
            return values.get(key.toString());
        }
    }

    private static Map<String, Object> read(Container container) {
        Meta meta = new Meta();
        meta.container.values.putAll(container.values);
        return PersistentData.read(meta);
    }

    @Test
    void thePdcApiIsFoundAndSupported() {
        assertThat(PersistentData.supported()).isTrue();
    }

    @Test
    @SuppressWarnings("unchecked")
    void everyPrimitiveIsTypedAndSorted() {
        Map<String, Object> out = read(new Container().with("p:tag", "v").with("p:b", (byte) 1).with("p:s", (short) 2)
                .with("p:i", 3).with("p:l", 4L).with("p:f", 1.5f).with("p:d", 2.5d));

        assertThat(out.keySet()).containsExactly("p:b", "p:d", "p:f", "p:i", "p:l", "p:s", "p:tag");
        assertThat(out.get("p:b")).isEqualTo(typed("byte", (byte) 1));
        assertThat(out.get("p:s")).isEqualTo(typed("short", (short) 2));
        assertThat(out.get("p:i")).isEqualTo(typed("int", 3));
        assertThat(out.get("p:l")).isEqualTo(typed("long", 4L));
        assertThat(out.get("p:f")).isEqualTo(typed("float", 1.5f));
        assertThat(out.get("p:d")).isEqualTo(typed("double", 2.5d));
        assertThat(out.get("p:tag")).isEqualTo(typed("string", "v"));
    }

    @Test
    void arraysAreListsAndANestedContainerIsRead() {
        Container inner = new Container().with("q:n", (byte) 7);
        Map<String, Object> out = read(new Container().with("p:b", new byte[] {1, -2}).with("p:i", new int[] {3})
                .with("p:l", new long[] {9000000000L}).with("p:c", inner));

        assertThat(out.get("p:b")).isEqualTo(typed("byte[]", Arrays.asList(1, -2)));
        assertThat(out.get("p:i")).isEqualTo(typed("int[]", Arrays.asList(3)));
        assertThat(out.get("p:l")).isEqualTo(typed("long[]", Arrays.asList(9000000000L)));
        assertThat(out.get("p:c")).isEqualTo(typed("container", Collections.singletonMap("q:n", typed("byte", (byte) 7))));
    }

    @Test
    void aValueOfNoKnownTypeIsReportedAsUnsupportedNotDropped() {
        Map<String, Object> out = read(new Container().with("p:odd", new Object[] {"x"}).with("p:ok", (byte) 1));

        assertThat(out).containsOnlyKeys("p:odd", "p:ok");
        Map<?, ?> odd = (Map<?, ?>) out.get("p:odd");
        assertThat(odd.get("type")).isEqualTo("unsupported");
        assertThat((String) odd.get("value")).contains("p:odd");
    }

    @Test
    void anEmptyContainerReadsAsNull() {
        assertThat(read(new Container())).isNull();
    }

    @Test
    void aPackagePrivateMetaClassIsReadThroughItsPublicInterface() {
        assertThat(PersistentData.read(new HiddenMeta())).containsOnlyKeys("p:b");
    }

    @Test
    void noMetaReadsAsNull() {
        assertThat(PersistentData.read(null)).isNull();
    }

    private static Map<String, Object> typed(String type, Object value) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("type", type);
        out.put("value", value);
        return out;
    }
}
