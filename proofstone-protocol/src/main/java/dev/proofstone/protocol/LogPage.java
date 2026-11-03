package dev.proofstone.protocol;

import java.util.List;

/** Reply of {@code GET /v1/log?since=&minLevel=} (log-record.json#/$defs/page). {@code next} is the cursor for the next read. */
public record LogPage(List<LogRecord> records, long next) {
}
