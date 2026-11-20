package io.github.h7kz.proofstone.core.junit;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The wire {@code item.pdc} as the values {@code ItemView.pdc()} holds (T-M11-05). The agent sends each entry as
 * {@code {"type", "value"}} because JSON loses the primitive type; here the type comes back as the Java type: byte,
 * short, int, long, float, double, String, byte[], int[], long[], a nested {@code Map} for a container, and for a value
 * the agent could not read a {@code Map} with the one key {@code "unsupported"} holding what it was. An entry of any
 * other shape or type is an error: nothing is guessed and nothing is dropped.
 */
final class PdcWire {

    private PdcWire() {
    }

    static Map<String, Object> values(Map<String, Object> wire) {
        if (wire == null) {
            return null;
        }
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : wire.entrySet()) {
            out.put(entry.getKey(), value(entry.getKey(), entry.getValue()));
        }
        return out;
    }

    private static Object value(String key, Object entry) {
        if (!(entry instanceof Map)) {
            throw bad(key, "is not a {type, value} object: " + entry);
        }
        Map<?, ?> typed = (Map<?, ?>) entry;
        Object type = typed.get("type");
        Object value = typed.get("value");
        if (!(type instanceof String) || value == null) {
            throw bad(key, "needs a string type and a value: " + entry);
        }
        switch ((String) type) {
            case "byte":
                return number(key, value).byteValue();
            case "short":
                return number(key, value).shortValue();
            case "int":
                return number(key, value).intValue();
            case "long":
                return number(key, value).longValue();
            case "float":
                return value instanceof String ? Float.valueOf((String) value) : number(key, value).floatValue();
            case "double":
                return value instanceof String ? Double.valueOf((String) value) : number(key, value).doubleValue();
            case "string":
                return text(key, value);
            case "byte[]": {
                List<?> list = list(key, value);
                byte[] out = new byte[list.size()];
                for (int i = 0; i < out.length; i++) {
                    out[i] = number(key, list.get(i)).byteValue();
                }
                return out;
            }
            case "int[]": {
                List<?> list = list(key, value);
                int[] out = new int[list.size()];
                for (int i = 0; i < out.length; i++) {
                    out[i] = number(key, list.get(i)).intValue();
                }
                return out;
            }
            case "long[]": {
                List<?> list = list(key, value);
                long[] out = new long[list.size()];
                for (int i = 0; i < out.length; i++) {
                    out[i] = number(key, list.get(i)).longValue();
                }
                return out;
            }
            case "container": {
                if (!(value instanceof Map)) {
                    throw bad(key, "container is not an object: " + value);
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> nested = (Map<String, Object>) value;
                return values(nested);
            }
            case "unsupported":
                return Map.of("unsupported", text(key, value));
            default:
                throw bad(key, "has the unknown type " + type);
        }
    }

    private static Number number(String key, Object value) {
        if (!(value instanceof Number)) {
            throw bad(key, "value is not a number: " + value);
        }
        return (Number) value;
    }

    private static String text(String key, Object value) {
        if (!(value instanceof String)) {
            throw bad(key, "value is not a string: " + value);
        }
        return (String) value;
    }

    private static List<?> list(String key, Object value) {
        if (!(value instanceof List)) {
            throw bad(key, "value is not an array: " + value);
        }
        return (List<?>) value;
    }

    private static IllegalStateException bad(String key, String why) {
        return new IllegalStateException("the agent's pdc entry " + key + " " + why);
    }
}
