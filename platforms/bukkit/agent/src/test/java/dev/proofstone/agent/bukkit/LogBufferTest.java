package dev.proofstone.agent.bukkit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import org.junit.jupiter.api.Test;

/**
 * Paper forwards every JUL record to Log4j. On 1.12.2 Log4j runs asynchronously, so the copy arrives on another
 * thread, before or after the JUL one, and a per-thread match misses it (T-M3-06, observed on Paper 1.12.2).
 */
class LogBufferTest {

    private static final long NOW = 1_700_000_000_000L;

    @Test
    void aLog4jCopyOnAnotherThreadAfterTheJulRecordIsDropped() throws InterruptedException {
        LogBuffer buffer = new LogBuffer();
        buffer.addJul(jul("plugin", "hello"));
        onOtherThread(() -> buffer.acceptLog4j(NOW, "WARN", "plugin", "hello", null));

        assertThat(messages(buffer)).containsExactly("hello");
    }

    @Test
    void aLog4jCopyThatArrivesBeforeTheJulRecordIsDroppedToo() throws InterruptedException {
        LogBuffer buffer = new LogBuffer();
        onOtherThread(() -> buffer.acceptLog4j(NOW, "WARN", "plugin", "hello", null));
        buffer.addJul(jul("plugin", "hello"));

        assertThat(messages(buffer)).containsExactly("hello");
    }

    @Test
    void twoJulRecordsWithTheSameTextAreTwoRecordsWhenOnlyOneCopyCameBack() throws InterruptedException {
        LogBuffer buffer = new LogBuffer();
        buffer.addJul(jul("plugin", "again"));
        buffer.addJul(jul("plugin", "again"));
        onOtherThread(() -> buffer.acceptLog4j(NOW, "WARN", "plugin", "again", null));

        assertThat(messages(buffer)).containsExactly("again", "again");
    }

    @Test
    void aLog4jRecordWithoutAJulTwinIsKept() {
        LogBuffer buffer = new LogBuffer();
        buffer.acceptLog4j(NOW, "INFO", "net.minecraft.server.Main", "Done", null);

        assertThat(messages(buffer)).containsExactly("Done");
    }

    @Test
    void aJulRecordFarInTimeFromTheLog4jRecordIsNotItsTwin() {
        LogBuffer buffer = new LogBuffer();
        buffer.acceptLog4j(NOW - 60_000, "WARN", "plugin", "hello", null);
        buffer.addJul(jul("plugin", "hello"));

        assertThat(messages(buffer)).containsExactly("hello", "hello");
    }

    private static LogRecord jul(String logger, String message) {
        LogRecord record = new LogRecord(Level.WARNING, message);
        record.setLoggerName(logger);
        record.setMillis(NOW);
        return record;
    }

    private static void onOtherThread(Runnable work) throws InterruptedException {
        Thread thread = new Thread(work, "Log4j2-TF-test");
        thread.start();
        thread.join();
    }

    @SuppressWarnings("unchecked")
    private static List<String> messages(LogBuffer buffer) {
        List<String> out = new java.util.ArrayList<>();
        for (Object record : (List<Object>) buffer.read(0, "TRACE").get("records")) {
            out.add(String.valueOf(((Map<String, Object>) record).get("message")));
        }
        return out;
    }
}
