package io.github.h7kz.proofstone.protocol;

/**
 * One captured log record (log-record.json). {@code time} is an ISO-8601 instant with {@code Z}; {@code thrown} is the
 * stack trace text, or null.
 */
public record LogRecord(String time, String level, String logger, String message, String thrown) {
}
