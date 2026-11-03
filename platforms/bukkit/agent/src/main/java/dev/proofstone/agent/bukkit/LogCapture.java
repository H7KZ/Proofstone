package dev.proofstone.agent.bukkit;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
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
    private Object rootLogger;

    /** Starts capturing. Never throws: a server without the expected log4j just yields no output. */
    static LogCapture start() {
        LogCapture capture = new LogCapture();
        try {
            capture.attach();
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            capture.rootLogger = null;
        }
        return capture;
    }

    private void attach() throws ReflectiveOperationException {
        Class<?> appender = Class.forName("org.apache.logging.log4j.core.Appender");
        Class<?> event = Class.forName("org.apache.logging.log4j.core.LogEvent");
        Class<?> state = Class.forName("org.apache.logging.log4j.core.LifeCycle$State");
        Method threadName = event.getMethod("getThreadName");
        Method message = event.getMethod("getMessage");
        Method formatted = message.getReturnType().getMethod("getFormattedMessage");

        Object proxy = Proxy.newProxyInstance(appender.getClassLoader(), new Class<?>[] {appender},
                (self, method, args) -> {
                    switch (method.getName()) {
                        case "append":
                            record(threadName, message, formatted, args[0]);
                            return null;
                        case "getName":
                        case "toString":
                            return name;
                        case "isStarted":
                        case "ignoreExceptions":
                            return true;
                        case "getState":
                            return Enum.valueOf(state.asSubclass(Enum.class), "STARTED");
                        case "hashCode":
                            return System.identityHashCode(self);
                        case "equals":
                            return self == args[0];
                        default:
                            return method.getReturnType() == boolean.class ? Boolean.FALSE : null;
                    }
                });

        Class<?> manager = Class.forName("org.apache.logging.log4j.LogManager");
        Object context = manager.getMethod("getContext", boolean.class).invoke(null, false);
        Object config = context.getClass().getMethod("getConfiguration").invoke(context);
        Object root = config.getClass().getMethod("getRootLogger").invoke(config);
        Class<?> filter = Class.forName("org.apache.logging.log4j.core.Filter");
        Class<?> level = Class.forName("org.apache.logging.log4j.Level");
        root.getClass().getMethod("addAppender", appender, level, filter).invoke(root, proxy, null, null);
        rootLogger = root;
    }

    private void record(Method threadName, Method message, Method formatted, Object logEvent) {
        try {
            if (!thread.equals(threadName.invoke(logEvent))) {
                return;
            }
            Object text = formatted.invoke(message.invoke(logEvent));
            if (text != null) {
                synchronized (lines) {
                    lines.add(ANSI.matcher(text.toString()).replaceAll(""));
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
        if (rootLogger == null) {
            return;
        }
        try {
            rootLogger.getClass().getMethod("removeAppender", String.class).invoke(rootLogger, name);
        } catch (ReflectiveOperationException | RuntimeException e) {
            // Best-effort; the appender only ever records matching-thread events into a discarded list.
        }
    }
}
