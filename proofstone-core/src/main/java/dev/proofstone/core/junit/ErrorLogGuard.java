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
 * Fails a test for the server errors logged while it ran (robustness R9). A record counts when it is WARN or worse
 * and matches nothing on the allowlist. The allowlist holds known vanilla noise only; an unknown record is never
 * skipped, because "no errors" must mean no errors.
 */
final class ErrorLogGuard {

    /** Vanilla noise, each with the reason it is allowed (docs/platforms/paper.md, Gotchas). */
    static final Map<Pattern, String> ALLOWLIST = Map.of(
            Pattern.compile("No key layers in MapLike"), "flat world without generator settings, 1.21.11+");

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
    static void check(PaperHost host, long cursor) {
        String problem = describe(read(host, cursor, "WARN").records());
        if (problem != null) {
            throw new AssertionError(problem);
        }
    }

    /** The failure text for the records that are not allowlisted, or {@code null} when there are none. */
    static String describe(List<LogRecord> records) {
        List<LogRecord> unexpected = records.stream().filter(record -> !allowed(record)).toList();
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

    static boolean allowed(LogRecord record) {
        for (Pattern pattern : ALLOWLIST.keySet()) {
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
