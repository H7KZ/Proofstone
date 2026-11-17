package io.github.h7kz.proofstone.core.host;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The log guard of a Geyser cell's two auxiliary containers, the stand-in and the driver host (ADR-0018 section 6; the server
 * has {@code ErrorLogGuard.Patterns.GEYSER}). A line fails the test that was running when it appeared when it is an
 * {@code ERROR}, the head of an uncaught exception, or the stand-in's {@code STUB refused} outside the {@code stand-in-absent}
 * fault, unless an allowlist entry matches it. Empty-then-grow (ADR-0016 section 7): an entry needs a reason and a real run.
 */
final class AuxLogGuard {

    /** The stand-in's refusal of a Microsoft name; expected only in the {@code stand-in-absent} fault (T-M9-11). */
    static final String REFUSED = "STUB refused";

    private static final Pattern ERROR_LEVEL = Pattern.compile("(^|[\\s\\[|])ERROR([\\s\\]|:]|$)");
    // A thrown type at the head of a line ("Exception in thread", "java.lang.X: msg", "Caused by: ..."), never an "at ..." frame.
    private static final Pattern THROWN = Pattern.compile(
            "^(Exception in thread |(Caused by: )?([\\w$]+\\.)+[\\w$]*(Exception|Error|Throwable)(:|$))");

    /** The stand-in's known lines: none yet, a line is added only from a real run with its reason (T-M10-06). */
    static final Map<Pattern, String> STAND_IN_ALLOWLIST = Map.of();
    /** The driver host's known lines: none yet, a line is added only from a real run with its reason (T-M10-06). */
    static final Map<Pattern, String> DRIVER_HOST_ALLOWLIST = Map.of();

    private AuxLogGuard() {
    }

    /**
     * @param container {@code stand-in} or {@code driver host}, for the message
     * @param lines the lines to judge
     * @param allowlist known lines, each pattern with its reason
     * @param refusalExpected true only in the {@code stand-in-absent} fault, which makes the stand-in log {@link #REFUSED}
     * @return what failed, or {@code null} when the lines are clean
     */
    static String violations(String container, List<String> lines, Map<Pattern, String> allowlist, boolean refusalExpected) {
        List<String> bad = new ArrayList<>();
        for (String line : lines) {
            if (offending(line, refusalExpected) && !allowed(line, allowlist)) {
                bad.add(line.strip());
            }
        }
        if (bad.isEmpty()) {
            return null;
        }
        StringBuilder text = new StringBuilder("the ").append(container).append(" logged ").append(bad.size())
                .append(" unexpected ERROR, uncaught exception or ").append(REFUSED).append(" line(s) during the test:");
        bad.forEach(line -> text.append("\n  ").append(line));
        return text.toString();
    }

    private static boolean offending(String line, boolean refusalExpected) {
        if (line.contains(REFUSED)) {
            return !refusalExpected;
        }
        return ERROR_LEVEL.matcher(line).find() || THROWN.matcher(line).find();
    }

    private static boolean allowed(String line, Map<Pattern, String> allowlist) {
        return allowlist.keySet().stream().anyMatch(pattern -> pattern.matcher(line).find());
    }
}
