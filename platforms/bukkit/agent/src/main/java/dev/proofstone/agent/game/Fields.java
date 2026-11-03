package dev.proofstone.agent.game;

import dev.proofstone.agent.protocol.AgentException;
import java.util.Collection;
import java.util.Map;

/**
 * Typed reads from a request body the server has already parsed to JSON-ready values. A missing required
 * field, a wrong type or an undeclared field is {@code INVALID_REQUEST} (spec §2). Nothing is defaulted or
 * guessed.
 */
public final class Fields {

    private Fields() {
    }

    public static AgentException invalid(String message) {
        return new AgentException(400, "INVALID_REQUEST", message, null);
    }

    /** Rejects any field the body does not declare, so a typo fails loudly instead of being dropped. */
    public static void onlyFields(Map<String, Object> body, Collection<String> allowed, String what) {
        for (String field : body.keySet()) {
            if (!allowed.contains(field)) {
                throw invalid(what + " has no field \"" + field + "\"");
            }
        }
    }

    public static String text(Map<String, Object> body, String field) {
        String value = optionalText(body, field);
        if (value == null) {
            throw invalid("\"" + field + "\" is required");
        }
        return value;
    }

    public static String optionalText(Map<String, Object> body, String field) {
        Object value = body.get(field);
        if (value == null) {
            return null;
        }
        if (!(value instanceof String)) {
            throw invalid("\"" + field + "\" must be a string");
        }
        return (String) value;
    }

    public static Integer integer(Map<String, Object> body, String field) {
        Object value = body.get(field);
        if (value == null) {
            return null;
        }
        if (!(value instanceof Integer)) {
            throw invalid("\"" + field + "\" must be an integer");
        }
        return (Integer) value;
    }

    public static Double number(Map<String, Object> body, String field) {
        Object value = body.get(field);
        if (value == null) {
            return null;
        }
        if (!(value instanceof Number)) {
            throw invalid("\"" + field + "\" must be a number");
        }
        return ((Number) value).doubleValue();
    }

    public static double requiredNumber(Map<String, Object> body, String field) {
        Double value = number(body, field);
        if (value == null) {
            throw invalid("\"" + field + "\" is required");
        }
        return value;
    }

    public static Boolean bool(Map<String, Object> body, String field) {
        Object value = body.get(field);
        if (value == null) {
            return null;
        }
        if (!(value instanceof Boolean)) {
            throw invalid("\"" + field + "\" must be a boolean");
        }
        return (Boolean) value;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> optionalObject(Map<String, Object> body, String field) {
        Object value = body.get(field);
        if (value == null) {
            return null;
        }
        if (!(value instanceof Map)) {
            throw invalid("\"" + field + "\" must be an object");
        }
        return (Map<String, Object>) value;
    }
}
