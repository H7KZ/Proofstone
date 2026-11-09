package io.github.h7kz.proofstone.agent.velocity;

import com.velocitypowered.api.event.EventHandler;
import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import io.github.h7kz.proofstone.agent.protocol.AgentException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Read-only event probes (spec §2, event.json). Each probe is a reflective handler at {@link PostOrder#LAST} that
 * records the event's zero-argument getters and returns at once: it never calls a setter, never returns a task that
 * would delay the event, and never changes it (ADR-0002, ADR-0016 section 10). A probe keeps the last
 * {@value #CAPACITY} events.
 *
 * <p>Thread model: Velocity fires events on its own pool, so a handler runs on whatever thread fired the event. The
 * ring buffer is synchronised, and the getters are read inline because an event is only safe to read during its own
 * dispatch. A proxy has no ticks, so {@code tick} is always 0.
 */
final class VelocityEventProbes {

    static final int CAPACITY = 1000;

    private final ProxyServer proxy;
    private final Object plugin;
    private final Map<String, Probe> probes = new ConcurrentHashMap<>();
    private final AtomicInteger nextId = new AtomicInteger(1);

    VelocityEventProbes(ProxyServer proxy, Object plugin) {
        this.proxy = proxy;
        this.plugin = plugin;
    }

    String subscribe(String className) {
        Class<?> type = eventClass(className);
        Probe probe = new Probe(type);
        String id = "probe-" + nextId.getAndIncrement();
        register(type, probe);
        probes.put(id, probe);
        return id;
    }

    @SuppressWarnings("unchecked")
    private void register(Class<?> type, Probe probe) {
        proxy.getEventManager().register(plugin, (Class<Object>) type, PostOrder.LAST, probe.handler);
    }

    void unsubscribe(String probeId) {
        Probe probe = probes.remove(probeId);
        if (probe == null) {
            throw notFound(probeId);
        }
        proxy.getEventManager().unregister(plugin, probe.handler);
    }

    Map<String, Object> events(String probeId, long since) {
        Probe probe = probes.get(probeId);
        if (probe == null) {
            throw notFound(probeId);
        }
        return probe.page(since);
    }

    void close() {
        for (Probe probe : probes.values()) {
            proxy.getEventManager().unregister(plugin, probe.handler);
        }
        probes.clear();
    }

    private static AgentException notFound(String probeId) {
        return new AgentException(404, "PROBE_NOT_FOUND", "no probe named " + probeId,
                Collections.singletonMap("probeId", probeId));
    }

    /** The agent's own loader first (it sees Velocity's API), then each plugin's. */
    private Class<?> eventClass(String className) {
        Class<?> found = load(className, getClass().getClassLoader());
        for (PluginContainer container : proxy.getPluginManager().getPlugins()) {
            if (found != null) {
                break;
            }
            Optional<?> instance = container.getInstance();
            if (instance.isPresent()) {
                found = load(className, instance.get().getClass().getClassLoader());
            }
        }
        if (found == null) {
            throw new AgentException(400, "INVALID_REQUEST", "no class named " + className, null);
        }
        return found;
    }

    private static Class<?> load(String name, ClassLoader loader) {
        try {
            return Class.forName(name, false, loader);
        } catch (ClassNotFoundException | LinkageError e) {
            return null;
        }
    }

    /** One subscription: its handler, its ring buffer and the sequence of the next event. */
    private static final class Probe {

        private final Class<?> type;
        private final Deque<Map<String, Object>> events = new ArrayDeque<>();
        private long total;
        /** Returns normally and at once; the default {@code executeAsync} then reports no task, so nothing is delayed. */
        private final EventHandler<Object> handler = this::record;

        Probe(Class<?> type) {
            this.type = type;
        }

        private synchronized void record(Object event) {
            Map<String, Object> captured = new LinkedHashMap<>();
            captured.put("class", type.getName());
            captured.put("tick", 0);
            captured.put("thread", Thread.currentThread().getName());
            captured.put("fields", fields(event));
            events.addLast(captured);
            total++;
            if (events.size() > CAPACITY) {
                events.removeFirst();
            }
        }

        synchronized Map<String, Object> page(long since) {
            long seq = total - events.size();
            List<Object> out = new ArrayList<>();
            for (Map<String, Object> event : events) {
                if (seq >= since) {
                    out.add(event);
                }
                seq++;
            }
            Map<String, Object> page = new LinkedHashMap<>();
            page.put("events", out);
            page.put("next", total);
            return page;
        }
    }

    /** The event's zero-argument public {@code get*} methods, by method name, as spec §3 {@code event.json} keeps them. */
    static Map<String, Object> fields(Object event) {
        List<Method> getters = new ArrayList<>();
        for (Method method : publicMethods(event.getClass())) {
            if (isGetter(method)) {
                getters.add(method);
            }
        }
        getters.sort(Comparator.comparing(Method::getName));
        Map<String, Object> out = new LinkedHashMap<>();
        for (Method getter : getters) {
            out.put(getter.getName(), value(getter, event));
        }
        return out;
    }

    /**
     * getMethods() resolves the types in every public signature, so one getter naming a class the proxy lacks makes it
     * throw for the whole event. Walking the hierarchy with getDeclaredMethods() fails per class instead.
     */
    private static List<Method> publicMethods(Class<?> type) {
        try {
            return Arrays.asList(type.getMethods());
        } catch (LinkageError e) {
            List<Method> methods = new ArrayList<>();
            for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
                try {
                    for (Method method : c.getDeclaredMethods()) {
                        if (Modifier.isPublic(method.getModifiers())) {
                            methods.add(method);
                        }
                    }
                } catch (LinkageError skipped) {
                    // This class has a signature the proxy cannot resolve; its superclasses may still be fine.
                }
            }
            return methods;
        }
    }

    private static boolean isGetter(Method method) {
        String name = method.getName();
        return method.getParameterTypes().length == 0
                && !Modifier.isStatic(method.getModifiers())
                && method.getReturnType() != void.class
                && name.startsWith("get")
                && !"getClass".equals(name)
                && method.getDeclaringClass() != Object.class;
    }

    /** A getter's value as JSON-ready data. A getter that throws is recorded as an {@code error} typed value. */
    private static Object value(Method getter, Object event) {
        Object result;
        try {
            result = getter.invoke(event);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            Throwable cause = e instanceof InvocationTargetException && e.getCause() != null ? e.getCause() : e;
            return typed("error", String.valueOf(cause));
        }
        return json(result);
    }

    private static Object json(Object result) {
        if (result == null || result instanceof String || result instanceof Boolean || result instanceof Number) {
            return result;
        }
        if (result instanceof Optional) {
            return json(((Optional<?>) result).orElse(null));
        }
        if (result instanceof Player) {
            return Collections.singletonMap("name", ((Player) result).getUsername());
        }
        if (result instanceof RegisteredServer) {
            return Collections.singletonMap("name", ((RegisteredServer) result).getServerInfo().getName());
        }
        if (result instanceof ServerConnection) {
            return Collections.singletonMap("name", ((ServerConnection) result).getServerInfo().getName());
        }
        return typed(result.getClass().getName(), String.valueOf(result));
    }

    private static Map<String, Object> typed(String type, String value) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("$type", type);
        out.put("value", value);
        return out;
    }
}
