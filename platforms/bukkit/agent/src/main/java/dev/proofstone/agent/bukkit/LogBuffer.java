package dev.proofstone.agent.bukkit;

import dev.proofstone.agent.protocol.AgentException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * Keeps the last {@value #CAPACITY} records the root JUL logger sees, with stack traces (spec §2, log-record.json).
 * Plugins log through JUL, and Paper bridges its own output into the same root, so this one handler sees both.
 * Log4j-native lines are not seen; the server's own messages go to the console, not to this buffer.
 */
final class LogBuffer {

    static final int CAPACITY = 5000;

    private static final List<String> ORDER = Arrays.asList("TRACE", "DEBUG", "INFO", "WARN", "ERROR", "FATAL");

    private final Deque<Entry> entries = new ArrayDeque<>();
    private final Handler handler = new Capture();
    /** Sequence number of the next record. The buffer holds the records with sequence [total - size, total). */
    private long total;

    /** Attaches to the root logger. Call once, from the plugin's enable. */
    static LogBuffer attach() {
        LogBuffer buffer = new LogBuffer();
        Logger.getLogger("").addHandler(buffer.handler);
        return buffer;
    }

    void detach() {
        Logger.getLogger("").removeHandler(handler);
    }

    /** Records at or after the cursor, at or above {@code minLevel} (spec §2). Throws on an unknown level. */
    synchronized Map<String, Object> read(long since, String minLevel) {
        int floor = ORDER.indexOf(minLevel);
        if (floor < 0) {
            throw new AgentException(400, "INVALID_REQUEST", "\"minLevel\" must be one of " + ORDER, null);
        }
        List<Object> out = new ArrayList<>();
        long seq = total - entries.size();
        for (Entry entry : entries) {
            if (seq >= since && ORDER.indexOf(entry.level) >= floor) {
                out.add(entry.json);
            }
            seq++;
        }
        Map<String, Object> page = new LinkedHashMap<>();
        page.put("records", out);
        page.put("next", total);
        return page;
    }

    private synchronized void add(LogRecord record) {
        String level = name(record.getLevel());
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("time", Instant.ofEpochMilli(record.getMillis()).toString());
        json.put("level", level);
        json.put("logger", record.getLoggerName() == null ? "" : record.getLoggerName());
        json.put("message", formatted(record));
        json.put("thrown", thrown(record.getThrown()));
        entries.addLast(new Entry(level, json));
        total++;
        if (entries.size() > CAPACITY) {
            entries.removeFirst();
        }
    }

    /** JUL levels to the protocol names (log-record.json). FINE and below map to DEBUG and TRACE. */
    static String name(Level level) {
        int value = level.intValue();
        if (value >= Level.SEVERE.intValue()) {
            return "ERROR";
        }
        if (value >= Level.WARNING.intValue()) {
            return "WARN";
        }
        if (value >= Level.INFO.intValue()) {
            return "INFO";
        }
        return value >= Level.FINE.intValue() ? "DEBUG" : "TRACE";
    }

    /** The message with its parameters applied, the way the JUL formatters do it. */
    private static String formatted(LogRecord record) {
        return new java.util.logging.Formatter() {
            @Override
            public String format(LogRecord r) {
                return formatMessage(r);
            }
        }.formatMessage(record);
    }

    private static String thrown(Throwable error) {
        if (error == null) {
            return null;
        }
        StringWriter text = new StringWriter();
        error.printStackTrace(new PrintWriter(text));
        return text.toString();
    }

    private static final class Entry {

        private final String level;
        private final Map<String, Object> json;

        Entry(String level, Map<String, Object> json) {
            this.level = level;
            this.json = json;
        }
    }

    private final class Capture extends Handler {

        @Override
        public void publish(LogRecord record) {
            add(record);
        }

        @Override
        public void flush() {
            // Nothing is buffered outside the deque.
        }

        @Override
        public void close() {
            // Detached by LogBuffer.detach.
        }
    }
}
