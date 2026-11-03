package dev.proofstone.agent.server;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Iterator;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * Java 8 twin of {@code GoldenMatcher} (proofstone-protocol testFixtures, Java 17, so the agent cannot use it).
 * Checks shape against the placeholders in {@code golden/README.md}. Keys must match exactly. Unknown
 * placeholders fail loudly.
 */
final class GoldenShape {

    private static final Pattern UUID = Pattern.compile(
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

    private GoldenShape() {
    }

    static void assertMatches(JsonNode expected, JsonNode actual) {
        match("$", expected, actual);
    }

    private static void match(String path, JsonNode expected, JsonNode actual) {
        if (expected.isTextual() && isPlaceholder(expected.asText())) {
            checkPlaceholder(path, expected.asText(), actual);
        } else if (expected.isObject()) {
            assertThat(actual.isObject()).as(path + " is an object").isTrue();
            assertThat(fieldNames(actual)).as(path + " keys").isEqualTo(fieldNames(expected));
            Iterator<String> names = expected.fieldNames();
            while (names.hasNext()) {
                String name = names.next();
                match(path + "." + name, expected.get(name), actual.get(name));
            }
        } else if (expected.isArray()) {
            assertThat(actual.isArray()).as(path + " is an array").isTrue();
            assertThat(actual.size()).as(path + " length").isEqualTo(expected.size());
            for (int i = 0; i < expected.size(); i++) {
                match(path + "[" + i + "]", expected.get(i), actual.get(i));
            }
        } else if (expected.isNumber()) {
            assertThat(actual.isNumber()).as(path + " is a number").isTrue();
            assertThat(actual.asDouble()).as(path).isEqualTo(expected.asDouble());
        } else {
            assertThat(actual).as(path).isEqualTo(expected);
        }
    }

    private static boolean isPlaceholder(String text) {
        return text.startsWith("<") && text.endsWith(">");
    }

    private static void checkPlaceholder(String path, String token, JsonNode actual) {
        switch (token) {
            case "<uuid>":
                assertThat(actual.isTextual() && UUID.matcher(actual.asText()).matches())
                        .as(path + " is a lower-case UUID: " + actual).isTrue();
                break;
            case "<time>":
                assertThat(isInstant(actual)).as(path + " is an ISO-8601 instant with Z: " + actual).isTrue();
                break;
            case "<requestId>":
            case "<string>":
                assertThat(actual.isTextual()).as(path + " is a string").isTrue();
                if ("<requestId>".equals(token)) {
                    assertThat(actual.asText().trim()).as(path + " is not blank").isNotEmpty();
                }
                break;
            default:
                throw new AssertionError("unknown golden placeholder " + token + " at " + path);
        }
    }

    private static boolean isInstant(JsonNode node) {
        if (!node.isTextual() || !node.asText().endsWith("Z")) {
            return false;
        }
        try {
            Instant.parse(node.asText());
            return true;
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    private static TreeSet<String> fieldNames(JsonNode node) {
        TreeSet<String> names = new TreeSet<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }
}
