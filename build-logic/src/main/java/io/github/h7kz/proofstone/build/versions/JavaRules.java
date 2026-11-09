package io.github.h7kz.proofstone.build.versions;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Optional;

/** Matches a Minecraft version against the {@code java-rules} of versions.yml. Spec: docs/specs/versions-yml.md. */
public final class JavaRules {

    private static final List<String> OPERATORS = List.of(">=", "<=", ">", "<", "=");

    private JavaRules() {
    }

    /** @return the Java image tag of the first matching rule, or empty when none matches (the caller fails loudly) */
    public static Optional<String> tagFor(JsonNode javaRules, String mc) {
        for (JsonNode rule : javaRules) {
            if (matches(rule.path("range").asText(), mc)) {
                return Optional.of(rule.path("image").asText());
            }
        }
        return Optional.empty();
    }

    // A range is space-separated terms, all of which must hold: ">=1.18 <1.20.5", "<=1.16.5", ">=26", "1.17.x".
    private static boolean matches(String range, String mc) {
        for (String term : range.trim().split("\\s+")) {
            if (!matchesTerm(term, mc)) {
                return false;
            }
        }
        return true;
    }

    private static boolean matchesTerm(String term, String mc) {
        String op = "=";
        String bound = term;
        for (String candidate : OPERATORS) {
            if (term.startsWith(candidate)) {
                op = candidate;
                bound = term.substring(candidate.length());
                break;
            }
        }
        if (bound.endsWith(".x")) {
            // "1.17.x" covers 1.17 and every 1.17.N.
            String prefix = bound.substring(0, bound.length() - 2);
            return op.equals("=") && (mc.equals(prefix) || mc.startsWith(prefix + "."));
        }
        int c = McVersions.compare(mc, bound);
        return switch (op) {
            case ">=" -> c >= 0;
            case "<=" -> c <= 0;
            case ">" -> c > 0;
            case "<" -> c < 0;
            default -> c == 0;
        };
    }
}
