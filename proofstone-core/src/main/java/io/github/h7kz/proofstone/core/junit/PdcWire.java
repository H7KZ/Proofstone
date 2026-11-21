package io.github.h7kz.proofstone.core.junit;

import java.math.BigDecimal;
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
                return (byte) integral(key, value, Byte.MIN_VALUE, Byte.MAX_VALUE, "byte");
            case "short":
                return (short) integral(key, value, Short.MIN_VALUE, Short.MAX_VALUE, "short");
            case "int":
                return (int) integral(key, value, Integer.MIN_VALUE, Integer.MAX_VALUE, "int");
            case "long":
                return integral(key, value, Long.MIN_VALUE, Long.MAX_VALUE, "long");
            case "float":
                return (float) floating(key, value, Float.MAX_VALUE, "float");
            case "double":
                return floating(key, value, Double.MAX_VALUE, "double");
            case "string":
                return text(key, value);
            case "byte[]": {
                List<?> list = list(key, value);
                byte[] out = new byte[list.size()];
                for (int i = 0; i < out.length; i++) {
                    out[i] = (byte) integral(key, list.get(i), Byte.MIN_VALUE, Byte.MAX_VALUE, "byte");
                }
                return out;
            }
            case "int[]": {
                List<?> list = list(key, value);
                int[] out = new int[list.size()];
                for (int i = 0; i < out.length; i++) {
                    out[i] = (int) integral(key, list.get(i), Integer.MIN_VALUE, Integer.MAX_VALUE, "int");
                }
                return out;
            }
            case "long[]": {
                List<?> list = list(key, value);
                long[] out = new long[list.size()];
                for (int i = 0; i < out.length; i++) {
                    out[i] = integral(key, list.get(i), Long.MIN_VALUE, Long.MAX_VALUE, "long");
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

    /**
     * A whole number within {@code [min, max]}. A fraction, a value out of range or a non-number is an error: a narrowing
     * cast would turn byte 300 into 44 and int 1.5 into 1, and a test would then assert on a value the server never held.
     */
    private static long integral(String key, Object value, long min, long max, String type) {
        if (!(value instanceof Number)) {
            throw bad(key, "value is not a number: " + value);
        }
        long out;
        try {
            out = new BigDecimal(value.toString()).longValueExact();
        } catch (NumberFormatException | ArithmeticException e) {
            throw bad(key, type + " value is not a whole number in range: " + value);
        }
        if (out < min || out > max) {
            throw bad(key, type + " value is out of range: " + value);
        }
        return out;
    }

    /**
     * A number within the type's range, or the strings Jackson writes for NaN and the infinities. Anything else is an error.
     */
    private static double floating(String key, Object value, double max, String type) {
        if (value instanceof String) {
            switch ((String) value) {
                case "NaN":
                    return Double.NaN;
                case "Infinity":
                    return Double.POSITIVE_INFINITY;
                case "-Infinity":
                    return Double.NEGATIVE_INFINITY;
                default:
                    throw bad(key, type + " value is not a number: " + value);
            }
        }
        if (!(value instanceof Number)) {
            throw bad(key, "value is not a number: " + value);
        }
        double out = ((Number) value).doubleValue();
        if (Math.abs(out) > max) {
            throw bad(key, type + " value is out of range: " + value);
        }
        return out;
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
