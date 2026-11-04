package dev.proofstone.api;

import java.util.Map;

/**
 * One event a probe captured (agent protocol event.json), immutable. {@code fields} holds the event's zero-argument
 * getters under the getter's own name, such as {@code getXpCost}.
 */
public record CapturedEvent(String className, long tick, String thread, Map<String, Object> fields) {

    public CapturedEvent {
        fields = Map.copyOf(fields);
    }

    /**
     * The numeric getter value, by getter name ({@code getXpCost}) or by the property name ({@code xpCost}).
     *
     * @throws IllegalStateException when the event has no such getter or it is not a whole number
     */
    public int getInt(String name) {
        Object value = field(name);
        if (value instanceof Number number && number.doubleValue() == number.intValue()) {
            return number.intValue();
        }
        throw new IllegalStateException(className + "." + name + " is not an integer: " + value);
    }

    /** The getter value, by getter name or property name. Fails loudly when the event has no such getter. */
    public Object field(String name) {
        Object value = fields.get(name);
        if (value == null && !name.startsWith("get") && !name.isEmpty()) {
            value = fields.get("get" + Character.toUpperCase(name.charAt(0)) + name.substring(1));
        }
        if (value == null) {
            throw new IllegalStateException(className + " has no getter \"" + name + "\"; captured: "
                    + fields.keySet());
        }
        return value;
    }
}
