package dev.proofstone.protocol.testing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Compares a response with its golden file. Volatile values are placeholders (golden/README.md). A placeholder
 * checks the shape of the value, so a malformed uuid fails. Unknown placeholders and unparsable input throw,
 * never pass (AGENTS.md §3, fail loudly).
 */
public final class GoldenMatcher {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Pattern PLACEHOLDER = Pattern.compile("^<[A-Za-z]+>$");
    private static final Pattern UUID = Pattern.compile(
            "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");
    /** Sample value for each placeholder, used to schema-validate a golden file. */
    /** JSON text of each sample, so a number placeholder is replaced by a bare number, not a string. */
    private static final Map<String, String> SAMPLES = Map.of(
            "<uuid>", "\"069a79f4-44e9-4726-a5be-fca90e38aaf5\"",
            "<time>", "\"2026-11-03T12:00:00Z\"",
            "<requestId>", "\"req-0001\"",
            "<string>", "\"sample\"",
            "<integer>", "0");

    private GoldenMatcher() {
    }

    /** @return one message per difference, each prefixed with its JSON path; empty when the actual matches. */
    public static List<String> mismatches(String expectedJson, String actualJson) {
        List<String> out = new ArrayList<>();
        compare("$", read(expectedJson), read(actualJson), out);
        return out;
    }

    /** Replaces every placeholder with a valid sample, so a golden file can be validated against a schema. */
    public static String concrete(String json) {
        String out = json;
        for (Map.Entry<String, String> sample : SAMPLES.entrySet()) {
            out = out.replace("\"" + sample.getKey() + "\"", sample.getValue());
        }
        return out;
    }

    private static void compare(String path, JsonNode expected, JsonNode actual, List<String> out) {
        if (expected.isTextual() && PLACEHOLDER.matcher(expected.asText()).matches()) {
            String placeholder = expected.asText();
            if (!SAMPLES.containsKey(placeholder)) {
                throw new IllegalArgumentException("unknown placeholder " + placeholder + " at " + path);
            }
            if (!fits(placeholder, actual)) {
                out.add(path + ": expected " + placeholder + " but was " + actual);
            }
        } else if (expected.isObject()) {
            compareObject(path, expected, actual, out);
        } else if (expected.isArray()) {
            compareArray(path, expected, actual, out);
        } else if (expected.isNumber() && actual.isNumber()) {
            if (expected.decimalValue().compareTo(actual.decimalValue()) != 0) {
                out.add(path + ": expected " + expected + " but was " + actual);
            }
        } else if (!expected.equals(actual)) {
            out.add(path + ": expected " + expected + " but was " + actual);
        }
    }

    private static void compareObject(String path, JsonNode expected, JsonNode actual, List<String> out) {
        if (!actual.isObject()) {
            out.add(path + ": expected object but was " + actual);
            return;
        }
        expected.fields().forEachRemaining(field -> {
            String next = path + "." + field.getKey();
            if (actual.has(field.getKey())) {
                compare(next, field.getValue(), actual.get(field.getKey()), out);
            } else {
                out.add(next + ": missing");
            }
        });
        actual.fields().forEachRemaining(field -> {
            if (!expected.has(field.getKey())) {
                out.add(path + "." + field.getKey() + ": unexpected " + field.getValue());
            }
        });
    }

    private static void compareArray(String path, JsonNode expected, JsonNode actual, List<String> out) {
        if (!actual.isArray()) {
            out.add(path + ": expected array but was " + actual);
            return;
        }
        if (expected.size() != actual.size()) {
            out.add(path + ": expected " + expected.size() + " items but was " + actual.size());
        }
        int shared = Math.min(expected.size(), actual.size());
        for (int i = 0; i < shared; i++) {
            compare(path + "[" + i + "]", expected.get(i), actual.get(i), out);
        }
    }

    private static boolean fits(String placeholder, JsonNode actual) {
        if ("<integer>".equals(placeholder)) {
            return actual.isIntegralNumber() && actual.asLong() >= 0;
        }
        if (!actual.isTextual()) {
            return false;
        }
        String value = actual.asText();
        return switch (placeholder) {
            case "<uuid>" -> UUID.matcher(value).matches();
            case "<time>" -> parsesAsInstant(value);
            case "<requestId>" -> !value.isBlank();
            default -> true;
        };
    }

    private static boolean parsesAsInstant(String value) {
        try {
            Instant.parse(value);
            return true;
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    private static JsonNode read(String json) {
        JsonNode node;
        try {
            node = JSON.readTree(json);
        } catch (IOException e) {
            throw new IllegalArgumentException("not JSON: " + json, e);
        }
        if (node == null || node.isMissingNode()) {
            throw new IllegalArgumentException("empty JSON document");
        }
        return node;
    }
}
