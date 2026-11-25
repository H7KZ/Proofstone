package io.github.h7kz.proofstone.agent.log;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.function.Consumer;

/**
 * An appender on the server's Log4j2 root logger, reached by reflection: the agent compiles against Bukkit 1.8.8
 * only and ships no logging dependency, and its bytecode stays Java 8. {@link #install} throws when the server has
 * no Log4j2 core; callers decide what a missing Log4j means for them. Each event is handed on as an opaque object,
 * read with the accessors below.
 */
public final class Log4jTap {

    /** Starts the marker line a capture logs behind a command's events (see {@link #emit}); buffers do not keep it. */
    public static final String CAPTURE_END_PREFIX = "proofstone-capture-end-";

    private final String name;
    private final Object root;
    private final Method threadName;
    private final Method message;
    private final Method formatted;
    private final Method level;
    private final Method loggerName;
    private final Method thrown;
    private final Method millis;

    private Log4jTap(String name, Object root, Class<?> event) throws ReflectiveOperationException {
        this.name = name;
        this.root = root;
        this.threadName = event.getMethod("getThreadName");
        this.message = event.getMethod("getMessage");
        this.formatted = message.getReturnType().getMethod("getFormattedMessage");
        this.level = event.getMethod("getLevel");
        this.loggerName = event.getMethod("getLoggerName");
        this.thrown = event.getMethod("getThrown");
        this.millis = event.getMethod("getTimeMillis");
    }

    /** Adds an appender named {@code name} that calls {@code sink} for every event, on the logging thread. */
    public static Log4jTap install(String name, Consumer<Object> sink) throws ReflectiveOperationException {
        Class<?> appender = Class.forName("org.apache.logging.log4j.core.Appender");
        Class<?> event = Class.forName("org.apache.logging.log4j.core.LogEvent");
        Class<?> state = Class.forName("org.apache.logging.log4j.core.LifeCycle$State");

        Object proxy = Proxy.newProxyInstance(appender.getClassLoader(), new Class<?>[] {appender},
                (self, method, args) -> {
                    switch (method.getName()) {
                        case "append":
                            sink.accept(args[0]);
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
        Class<?> levelType = Class.forName("org.apache.logging.log4j.Level");
        root.getClass().getMethod("addAppender", appender, levelType, filter).invoke(root, proxy, null, null);
        return new Log4jTap(name, root, event);
    }

    /**
     * Logs {@code message} at INFO through Log4j's own logger on the calling thread, so it takes the same route as every
     * event the server logs. With Log4j's async loggers (Paper 1.16.5 on Java 8) events reach an appender on a background
     * thread after the call that logged them has returned; the queue is first in, first out, so seeing this message in the
     * appender means every earlier event of this thread was delivered first (T-M12-12).
     */
    public void emit(String message) throws ReflectiveOperationException {
        Class<?> manager = Class.forName("org.apache.logging.log4j.LogManager");
        Class<?> loggerType = Class.forName("org.apache.logging.log4j.Logger");
        Object logger = manager.getMethod("getLogger", String.class).invoke(null, "ProofstoneAgent");
        loggerType.getMethod("info", String.class).invoke(logger, message);
    }

    /** Best effort: the appender only ever records events into buffers that are discarded with it. */
    public void remove() {
        try {
            root.getClass().getMethod("removeAppender", String.class).invoke(root, name);
        } catch (ReflectiveOperationException | RuntimeException e) {
            // Nothing to undo if the logger is already gone.
        }
    }

    public String thread(Object event) throws ReflectiveOperationException {
        return (String) threadName.invoke(event);
    }

    /** The formatted message, or {@code null} when the event has none. */
    public String text(Object event) throws ReflectiveOperationException {
        Object text = formatted.invoke(message.invoke(event));
        return text == null ? null : text.toString();
    }

    /** The Log4j level name, such as {@code WARN}. */
    public String level(Object event) throws ReflectiveOperationException {
        return String.valueOf(level.invoke(event));
    }

    public String logger(Object event) throws ReflectiveOperationException {
        Object value = loggerName.invoke(event);
        return value == null ? "" : value.toString();
    }

    public Throwable thrown(Object event) throws ReflectiveOperationException {
        return (Throwable) thrown.invoke(event);
    }

    public long millis(Object event) throws ReflectiveOperationException {
        return (Long) millis.invoke(event);
    }
}
