package io.github.h7kz.proofstone.agent.bukkit;

import io.github.h7kz.proofstone.agent.log.Log4jTap;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Best-effort capture of what a console command logs. Vanilla commands cannot run for a custom
 * {@code CommandSender} (the server refuses to make it a vanilla command listener), so they run as the real
 * console and their output goes to the server log. This collects the log events the calling thread produces
 * while the command runs.
 *
 * <p>Log4j is the server's own logger, so it is reached by reflection: the agent compiles against Bukkit 1.8.8
 * only and ships no logging dependency. If any of it is missing the capture is silently empty, which the protocol
 * allows (spec section 2: output is best-effort).
 */
final class LogCapture implements AutoCloseable {

    private static final Pattern ANSI = Pattern.compile("\u001B\\[[;\\d]*m");

    private final List<String> lines = new ArrayList<>();
    private final String thread = Thread.currentThread().getName();
    private final String name = "ProofstoneCapture-" + UUID.randomUUID();
    /** Logged by {@link #close()} behind the command's own events; seeing it means they were all delivered (async loggers). */
    private final String sentinel = Log4jTap.CAPTURE_END_PREFIX + UUID.randomUUID();
    private final CountDownLatch drained = new CountDownLatch(1);
    /** How long {@link #close()} waits for the sentinel; a synchronous logger delivers it before emit returns. */
    private static final long DRAIN_MILLIS = 2000;
    /** Set right after the appender is installed; events from other threads can arrive before that. */
    private volatile Log4jTap tap;

    /** Starts capturing. Never throws: a server without the expected log4j just yields no output. */
    static LogCapture start() {
        LogCapture capture = new LogCapture();
        try {
            capture.attach();
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            capture.tap = null;
        }
        return capture;
    }

    private void attach() throws ReflectiveOperationException {
        tap = Log4jTap.install(name, this::record);
    }

    private void record(Object logEvent) {
        Log4jTap source = tap;
        if (source == null) {
            return;
        }
        try {
            if (!thread.equals(source.thread(logEvent))) {
                return;
            }
            String text = source.text(logEvent);
            if (text != null && text.contains(sentinel)) {
                drained.countDown();
                return;
            }
            if (text != null) {
                synchronized (lines) {
                    lines.add(ANSI.matcher(text).replaceAll(""));
                }
            }
        } catch (ReflectiveOperationException e) {
            // Best-effort: an unreadable event is dropped.
        }
    }

    /** What was logged on the calling thread since {@link #start()}, one entry per line. */
    List<String> lines() {
        synchronized (lines) {
            return new ArrayList<>(lines);
        }
    }

    @Override
    public void close() {
        Log4jTap source = tap;
        if (source == null) {
            return;
        }
        try {
            source.emit(sentinel);
            if (!drained.await(DRAIN_MILLIS, TimeUnit.MILLISECONDS)) {
                // Best-effort capture (spec section 2): what arrived is returned. The wait is the pipeline draining, not a
                // retry; an event still queued after it is not part of this command's output.
                org.bukkit.Bukkit.getLogger().warning("log capture: the command's log events were not all delivered in "
                        + DRAIN_MILLIS + " ms");
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            // Best effort (spec section 2), but not silent: a reply that may be incomplete is named in the server log.
            org.bukkit.Bukkit.getLogger().warning("log capture: could not log the end marker, the command's reply may be "
                    + "incomplete: " + e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            source.remove();
        }
    }
}
