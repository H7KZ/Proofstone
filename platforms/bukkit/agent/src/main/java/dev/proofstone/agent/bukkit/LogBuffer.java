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
 * Keeps the last {@value #CAPACITY} records the root JUL logger and the Log4j2 root logger see, with stack traces
 * (spec §2, log-record.json). Plugins log through JUL; the server's own messages go through Log4j directly. Paper
 * forwards every JUL record to Log4j as well, so a record can arrive twice on one thread, once from each side. The
 * second copy is dropped, matched on level, logger and message against the last record the other side delivered on
 * that thread. Without Log4j2 on the server only JUL is read.
 */
final class LogBuffer {

    static final int CAPACITY = 5000;

    private static final List<String> ORDER = Arrays.asList("TRACE", "DEBUG", "INFO", "WARN", "ERROR", "FATAL");

    private final Deque<Entry> entries = new ArrayDeque<>();
    private final Handler handler = new Capture();
    private final String tapName = "ProofstoneLog-" + java.util.UUID.randomUUID();
    /** Last record each side delivered on this thread, to drop the forwarded copy from the other side. */
    private final ThreadLocal<String> lastJul = new ThreadLocal<>();
    private final ThreadLocal<String> lastLog4j = new ThreadLocal<>();
    private volatile Log4jTap tap;
    /** Sequence number of the next record. The buffer holds the records with sequence [total - size, total). */
    private long total;

    /** Attaches to the root logger. Call once, from the plugin's enable. */
    static LogBuffer attach() {
        LogBuffer buffer = new LogBuffer();
        Logger.getLogger("").addHandler(buffer.handler);
        try {
            buffer.tap = Log4jTap.install(buffer.tapName, buffer::addLog4j);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            buffer.tap = null; // no Log4j2 core on this server: JUL only
        }
        return buffer;
    }

    void detach() {
        Logger.getLogger("").removeHandler(handler);
        Log4jTap installed = tap;
        if (installed != null) {
            installed.remove();
        }
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

    private void addJul(LogRecord record) {
        String level = name(record.getLevel());
        String logger = record.getLoggerName() == null ? "" : record.getLoggerName();
        String message = formatted(record);
        String key = key(level, logger, message);
        if (key.equals(lastLog4j.get())) {
            lastLog4j.remove(); // Log4j delivered this record first; Paper's JUL forwarder is why JUL sees it again
            return;
        }
        lastJul.set(key);
        add(record.getMillis(), level, logger, message, thrown(record.getThrown()));
    }

    private void addLog4j(Object event) {
        Log4jTap source = tap;
        if (source == null) {
            return;
        }
        try {
            String message = source.text(event);
            if (message == null) {
                message = "";
            }
            // An unknown level is kept as WARN, so it is never silently below a reader's floor.
            String level = ORDER.contains(source.level(event)) ? source.level(event) : "WARN";
            String logger = source.logger(event);
            String key = key(level, logger, message);
            if (key.equals(lastJul.get())) {
                lastJul.remove();
                return;
            }
            lastLog4j.set(key);
            add(source.millis(event), level, logger, message, thrown(source.thrown(event)));
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("cannot read a Log4j event", e);
        }
    }

    private static String key(String level, String logger, String message) {
        return level + '\n' + logger + '\n' + message;
    }

    private synchronized void add(long millis, String level, String logger, String message, String thrown) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("time", Instant.ofEpochMilli(millis).toString());
        json.put("level", level);
        json.put("logger", logger);
        json.put("message", message);
        json.put("thrown", thrown);
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
            addJul(record);
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
