package dev.proofstone.agent.bukkit;

import dev.proofstone.agent.protocol.AgentException;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Block state on a server before 1.13, where a state is the block's data value (T-M3-06). The table is a resource
 * (legacy-block-states.csv): per material, one property, the bits of the data value it owns and its values. A state
 * the table cannot express is UNKNOWN_KEY, never dropped (spec section 2).
 */
final class LegacyBlocks {

    /** One property of one material. */
    private static final class Property {
        final String name;
        final int mask;
        final Map<String, Integer> values = new LinkedHashMap<>();

        Property(String name, int mask) {
            this.name = name;
            this.mask = mask;
        }
    }

    private static final Map<String, Property> PROPERTIES = load();

    private LegacyBlocks() {
    }

    /**
     * The data value that writes {@code state}, keeping the bits the table does not own (an anvil's damage).
     *
     * @param current the block's current data value
     */
    static int data(String material, Map<String, String> state, int current) {
        if (state.isEmpty()) {
            return current;
        }
        Property property = PROPERTIES.get(material);
        for (Map.Entry<String, String> entry : state.entrySet()) {
            Integer value = property != null && property.name.equals(entry.getKey())
                    ? property.values.get(entry.getValue()) : null;
            if (value == null) {
                throw new AgentException(400, "UNKNOWN_KEY",
                        "block state " + entry.getKey() + "=" + entry.getValue() + " of " + material
                                + " is not in the legacy table",
                        Collections.singletonMap("key", entry.getKey() + "=" + entry.getValue()));
            }
        }
        return (current & ~property.mask) | property.values.get(state.get(property.name));
    }

    /** The state a data value stands for: empty for a material without a row. */
    static Map<String, String> state(String material, int data) {
        Property property = PROPERTIES.get(material);
        Map<String, String> state = new TreeMap<>();
        if (property == null) {
            return state;
        }
        for (Map.Entry<String, Integer> value : property.values.entrySet()) {
            if (value.getValue() == (data & property.mask)) {
                state.put(property.name, value.getKey());
                return state;
            }
        }
        // A data value with no row (a chest never faces up) is a surprise, not an empty state.
        throw new AgentException(500, "INTERNAL",
                "data value " + data + " of " + material + " has no " + property.name + " in the legacy table", null);
    }

    private static Map<String, Property> load() {
        Map<String, Property> out = new HashMap<>();
        for (String[] row : LegacyNames.rows("legacy-block-states.csv", ";")) {
            if (row.length != 4) {
                throw new IllegalStateException("bad row in legacy-block-states.csv: " + String.join(";", row));
            }
            Property property = new Property(row[1], Integer.parseInt(row[2]));
            for (String pair : row[3].split(",")) {
                String[] value = pair.split("=");
                property.values.put(value[0], Integer.parseInt(value[1]));
            }
            out.put(row[0], property);
        }
        return out;
    }
}
