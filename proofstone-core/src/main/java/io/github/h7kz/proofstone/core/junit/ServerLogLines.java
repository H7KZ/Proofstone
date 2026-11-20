package io.github.h7kz.proofstone.core.junit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.h7kz.proofstone.core.await.Await;
import io.github.h7kz.proofstone.core.host.AgentEndpoint;
import io.github.h7kz.proofstone.protocol.LogPage;
import io.github.h7kz.proofstone.protocol.LogRecord;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.LongSupplier;
import java.util.regex.Pattern;

/**
 * {@code Agent.logLines} over the agent's {@code GET /v1/log} (agent-protocol section 2, T-M11-01). It reads at the TRACE
 * floor, so every record since the test's cursor comes back, and renders each as plain text lines.
 */
final class ServerLogLines {

    private static final ObjectMapper JSON = new ObjectMapper();

    private ServerLogLines() {
    }

    /** The lines of every record logged since {@code cursor}. @throws IllegalStateException when records were dropped */
    static List<String> read(AgentEndpoint host, long cursor) {
        LogPage page;
        try {
            page = JSON.treeToValue(host.request("GET", "/v1/log?since=" + cursor + "&minLevel=TRACE", null),
                    LogPage.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("unparsable agent log reply", e);
        }
        // Sequence numbers are dense and TRACE is the floor, so a short page means the agent's ring buffer evicted records
        // the test had not read. An incomplete log must never read as a complete one (AGENTS.md section 3).
        long expected = page.next() - cursor;
        if (page.records().size() != expected) {
            throw new IllegalStateException("the agent's log buffer dropped " + (expected - page.records().size())
                    + " of " + expected + " records logged since the test started, so the log lines are incomplete");
        }
        return render(page.records());
    }

    /** Waits for a line matching {@code pattern}, then returns every matching line. */
    static List<String> await(AgentEndpoint host, LongSupplier cursor, Pattern pattern, Duration timeout) {
        List<String> lines = Await.until("a server log line matching /" + pattern.pattern() + "/",
                () -> read(host, cursor.getAsLong()), all -> all.stream().anyMatch(line -> pattern.matcher(line).find()),
                timeout);
        return lines.stream().filter(line -> pattern.matcher(line).find()).toList();
    }

    /** One line per line of text: {@code LEVEL logger: message}, then the rest of the message, then the stack trace. */
    static List<String> render(List<LogRecord> records) {
        List<String> lines = new ArrayList<>();
        for (LogRecord record : records) {
            String text = record.level() + " " + record.logger() + ": " + record.message();
            if (record.thrown() != null) {
                text += "\n" + record.thrown();
            }
            for (String line : text.split("\\R", -1)) {
                lines.add(line);
            }
        }
        return List.copyOf(lines);
    }
}
