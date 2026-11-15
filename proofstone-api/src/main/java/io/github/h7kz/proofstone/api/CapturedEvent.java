package io.github.h7kz.proofstone.api;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One event a probe captured (agent protocol event.json), immutable. {@code fields} holds the event's zero-argument
 * getters under the getter's own name, such as {@code getXpCost}.
 */
public record CapturedEvent(String className, long tick, String thread, Map<String, Object> fields) {

    public CapturedEvent {
        // Not Map.copyOf: a getter that returned null is a value the agent captured, and copyOf refuses it (T-M8-10).
        fields = Collections.unmodifiableMap(new LinkedHashMap<>(fields));
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

    /**
     * The getter value, by getter name or property name; {@code null} when the getter returned null. Fails loudly when the
     * event has no such getter.
     */
    public Object field(String name) {
        if (fields.containsKey(name)) {
            return fields.get(name);
        }
        String getter = name.isEmpty() || name.startsWith("get") ? null
                : "get" + Character.toUpperCase(name.charAt(0)) + name.substring(1);
        if (getter != null && fields.containsKey(getter)) {
            return fields.get(getter);
        }
        throw new IllegalStateException(className + " has no getter \"" + name + "\"; captured: " + fields.keySet());
    }
}
