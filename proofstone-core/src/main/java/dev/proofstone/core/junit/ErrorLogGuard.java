package dev.proofstone.core.junit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.proofstone.core.host.PaperHost;
import dev.proofstone.protocol.LogPage;
import dev.proofstone.protocol.LogRecord;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Fails a test for the server errors logged while it ran (robustness R9). A record counts when it is WARN or worse and
 * is not allowed: it matches an error pattern of its platform pack, or it matches nothing on that pack's allowlist. The
 * allowlist holds known vanilla noise only; an unknown record is never skipped, because "no errors" must mean no errors.
 */
final class ErrorLogGuard {

    /**
     * The log patterns one platform pack supplies (robustness R8, R9). Each pack owns its list: a platform without one
     * fails loudly in {@link #of}, it never gets a silent default.
     *
     * @param platform     the platform id, for example {@code paper}
     * @param allowlist    known noise, each pattern with the reason it is allowed
     * @param errorPatterns records that fail the test even when the allowlist matches, such as a thread-check violation
     */
    record Patterns(String platform, Map<Pattern, String> allowlist, List<Pattern> errorPatterns) {

        /**
         * Paper (docs/platforms/paper.md, Gotchas): vanilla noise only, and no error patterns yet.
         */
        static final Patterns PAPER = new Patterns("paper", Map.of(
                Pattern.compile("No key layers in MapLike"), "flat world without generator settings, 1.21.11+",
                // Only the harness's own clients, named by CellInvocation.joinClient ("T" and 8 hex digits). The reset
                // teleport races the client's first movement packets, and the Log4j capture (T-M2-08) shows the
                // server's anti-cheat WARN for it. Any other player, or a plugin's own message, still fails the test.
                Pattern.compile("^T[0-9a-f]{8} moved too quickly! "), "reset teleport races the harness client's move",
                // A Clients client (SIMULATED_BEDROCK) is named with a leading '.' and never reset, so its first move
                // packets race the join position. Only the prefixed names a test chose match, never a plain player.
                Pattern.compile("^\\.[A-Za-z0-9_]{1,15} moved too quickly! "), "first move of a prefixed harness client"),
                List.of());

        /**
         * Folia (docs/platforms/folia.md, Gotchas), reviewed on its own, not copied from PAPER. A thread-check failure is
         * an error, never allowlisted (T-M4-15): Folia's TickThread logs "Thread failed main thread check" whenever
         * data is touched off its region thread, and that is a harness or plugin bug. The allowlist holds only noise
         * that a reviewed Folia run showed and the vanilla server also produces.
         */
        static final Patterns FOLIA = new Patterns("folia", Map.of(
                // Observed on folia@26.3 in :conformance:smoke (T-M5-03): "T94d99d12 moved too quickly! -1.5,-60.0,2.5".
                // Same anti-cheat WARN as Paper: the reset teleportAsync races the client's first movement packets. Only
                // the harness's own clients ("T" and 8 hex digits, CellInvocation.joinClient); any other player, or a
                // plugin's own message, still fails the test. The boot ERRORs (No key layers, bukkit.yml) are logged
                // before the test window and are not allowed here.
                Pattern.compile("^T[0-9a-f]{8} moved too quickly! "), "reset teleport races the harness client's move"),
                List.of(Pattern.compile("Thread failed main thread check"),
                        Pattern.compile("Must use teleportAsync while in region threading")));

        /**
         * Purpur (docs/platforms/purpur.md, Gotchas), reviewed on its own, not copied from PAPER. Its allowlist holds only
         * what a reviewed Purpur run showed; the entries are filled in from observed runs (T-M5-04).
         */
        static final Patterns PURPUR = new Patterns("purpur", Map.of(
                // Observed on purpur@26.3 (T-M5-04): "T0cd7aebd moved too quickly! 6.5,-60.0,-2.5" in C02, C04, C07, C08.
                // The same anti-cheat WARN as Paper and Folia, for the harness's own clients only (CellInvocation.joinClient).
                Pattern.compile("^T[0-9a-f]{8} moved too quickly! "), "reset teleport races the harness client's move"),
                List.of());

        /**
         * Spigot (docs/platforms/spigot.md, Gotchas), reviewed on its own. It is built by BuildTools, not by itzg, and its
         * allowlist holds only what a reviewed Spigot run showed (T-M5-05).
         */
        static final Patterns SPIGOT = new Patterns("spigot", Map.of(
                // Observed on spigot@26.3 in :conformance:smoke (T-M5-05): "T94d99d50 moved too quickly! -7.5,-60.0,6.5".
                // The same anti-cheat WARN as Paper, Folia and Purpur: the reset teleport races the harness client's first
                // movement packets. Harness clients only (CellInvocation.joinClient).
                Pattern.compile("^T[0-9a-f]{8} moved too quickly! "), "reset teleport races the harness client's move"),
                List.of());

        /** The patterns of a platform id. @throws IllegalStateException for a platform with no list */
        static Patterns of(String platform) {
            return switch (platform) {
                case "paper" -> PAPER;
                case "folia" -> FOLIA;
                case "purpur" -> PURPUR;
                case "spigot" -> SPIGOT;
                default -> throw new IllegalStateException("no log patterns for platform " + platform
                        + ": its platform pack must supply its own list (robustness R9)");
            };
        }
    }

    /** A cursor value past any record, so reading from it returns no records and the current end. */
    private static final long END = Long.MAX_VALUE;
    private static final ObjectMapper JSON = new ObjectMapper();

    private ErrorLogGuard() {
    }

    /** The cursor of the end of the server log now: records logged later have a greater sequence number. */
    static long cursor(PaperHost host) {
        return read(host, END, "FATAL").next();
    }

    /** @throws AssertionError naming every unexpected record logged since the cursor */
    static void check(PaperHost host, long cursor, Patterns patterns) {
        String problem = describe(read(host, cursor, "WARN").records(), patterns);
        if (problem != null) {
            throw new AssertionError(problem);
        }
    }

    /** The failure text for the records that are not allowed, or {@code null} when there are none. */
    static String describe(List<LogRecord> records, Patterns patterns) {
        List<LogRecord> unexpected = records.stream().filter(record -> !allowed(record, patterns)).toList();
        if (unexpected.isEmpty()) {
            return null;
        }
        StringBuilder text = new StringBuilder("server logged ").append(unexpected.size())
                .append(" unexpected WARN or worse record(s) during the test:");
        for (LogRecord record : unexpected) {
            text.append("\n  ").append(record.level()).append(' ').append(record.logger()).append(": ")
                    .append(record.message());
            if (record.thrown() != null) {
                text.append("\n    ").append(record.thrown().replace("\n", "\n    "));
            }
        }
        return text.toString();
    }

    /** An error pattern wins over the allowlist: a record that matches one is never allowed. */
    static boolean allowed(LogRecord record, Patterns patterns) {
        for (Pattern pattern : patterns.errorPatterns()) {
            if (pattern.matcher(record.message()).find()) {
                return false;
            }
        }
        for (Pattern pattern : patterns.allowlist().keySet()) {
            if (pattern.matcher(record.message()).find()) {
                return true;
            }
        }
        return false;
    }

    private static LogPage read(PaperHost host, long since, String minLevel) {
        try {
            return JSON.treeToValue(host.request("GET", "/v1/log?since=" + since + "&minLevel=" + minLevel, null),
                    LogPage.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("unparsable agent log reply", e);
        }
    }
}
