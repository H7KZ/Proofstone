package io.github.h7kz.proofstone.agent.bungee;

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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.PendingConnection;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.connection.Server;
import net.md_5.bungee.api.event.ChatEvent;
import net.md_5.bungee.api.event.LoginEvent;
import net.md_5.bungee.api.event.PlayerDisconnectEvent;
import net.md_5.bungee.api.event.PluginMessageEvent;
import net.md_5.bungee.api.event.PostLoginEvent;
import net.md_5.bungee.api.event.PreLoginEvent;
import net.md_5.bungee.api.event.ServerConnectEvent;
import net.md_5.bungee.api.event.ServerConnectedEvent;
import net.md_5.bungee.api.event.ServerDisconnectEvent;
import net.md_5.bungee.api.event.ServerKickEvent;
import net.md_5.bungee.api.event.ServerSwitchEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.event.EventHandler;
import net.md_5.bungee.event.EventPriority;

/**
 * Read-only event probes (spec §2, event.json). BungeeCord finds handlers by scanning a listener for annotated methods,
 * so, unlike on Bukkit and Velocity, a probe cannot be attached to an arbitrary event class by name: this listener has one
 * annotated method per event the proxy tests care about ({@link #SUPPORTED}), and a probe records the events of its class.
 * Asking for any other class is {@code INVALID_REQUEST} naming the supported ones, never a silent probe that sees nothing.
 *
 * <p>Every handler is at {@link EventPriority#HIGHEST}, the last to run on BungeeCord (a higher number runs later, the
 * reverse of the Bukkit names), so a probe sees the event as every other plugin left it. It records the event's
 * zero-argument getters and returns at once: it never calls a setter, never cancels, and never registers an intent that
 * would delay the event (ADR-0002, ADR-0016 section 10). A probe keeps the last {@value #CAPACITY} events.
 *
 * <p>Thread model: BungeeCord fires events on whichever thread fires them (Netty event loops, its scheduler). The ring
 * buffer is synchronised, and the getters are read inline because an event is only safe to read during its own dispatch.
 * A proxy has no ticks, so {@code tick} is always 0. Public with public handlers because the event bus invokes them by
 * reflection.
 */
public final class EventProbes implements Listener {

    static final int CAPACITY = 1000;

    /** The events a probe can watch, by class name. */
    static final List<Class<?>> SUPPORTED = Collections.unmodifiableList(Arrays.<Class<?>>asList(
            PreLoginEvent.class, LoginEvent.class, PostLoginEvent.class, PlayerDisconnectEvent.class,
            ServerConnectEvent.class, ServerConnectedEvent.class, ServerSwitchEvent.class,
            ServerDisconnectEvent.class, ServerKickEvent.class, ChatEvent.class, PluginMessageEvent.class));

    private final Map<String, Probe> probes = new ConcurrentHashMap<>();
    private final AtomicInteger nextId = new AtomicInteger(1);

    static EventProbes install(Plugin plugin) {
        EventProbes probes = new EventProbes();
        plugin.getProxy().getPluginManager().registerListener(plugin, probes);
        return probes;
    }

    String subscribe(String className) {
        Class<?> type = null;
        for (Class<?> supported : SUPPORTED) {
            if (supported.getName().equals(className)) {
                type = supported;
            }
        }
        if (type == null) {
            List<String> names = new ArrayList<>();
            for (Class<?> supported : SUPPORTED) {
                names.add(supported.getSimpleName());
            }
            throw new AgentException(400, "INVALID_REQUEST", "BungeeCord probes can watch only these events of "
                    + "net.md_5.bungee.api.event: " + names + ", not " + className, null);
        }
        String id = "probe-" + nextId.getAndIncrement();
        probes.put(id, new Probe(type));
        return id;
    }

    void unsubscribe(String probeId) {
        if (probes.remove(probeId) == null) {
            throw notFound(probeId);
        }
    }

    Map<String, Object> events(String probeId, long since) {
        Probe probe = probes.get(probeId);
        if (probe == null) {
            throw notFound(probeId);
        }
        return probe.page(since);
    }

    void close(Plugin plugin) {
        plugin.getProxy().getPluginManager().unregisterListener(this);
        probes.clear();
    }

    private static AgentException notFound(String probeId) {
        return new AgentException(404, "PROBE_NOT_FOUND", "no probe named " + probeId,
                Collections.singletonMap("probeId", probeId));
    }

    // One handler per supported event, all at the last priority, all just recording.

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPreLogin(PreLoginEvent event) {
        record(event);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onLogin(LoginEvent event) {
        record(event);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPostLogin(PostLoginEvent event) {
        record(event);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDisconnect(PlayerDisconnectEvent event) {
        record(event);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onServerConnect(ServerConnectEvent event) {
        record(event);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onServerConnected(ServerConnectedEvent event) {
        record(event);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onServerSwitch(ServerSwitchEvent event) {
        record(event);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onServerDisconnect(ServerDisconnectEvent event) {
        record(event);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onServerKick(ServerKickEvent event) {
        record(event);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onChat(ChatEvent event) {
        record(event);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPluginMessage(PluginMessageEvent event) {
        record(event);
    }

    private void record(Object event) {
        for (Probe probe : probes.values()) {
            if (probe.type.isInstance(event)) {
                probe.record(event);
            }
        }
    }

    /** One subscription: its class, its ring buffer and the sequence of the next event. */
    private static final class Probe {

        private final Class<?> type;
        private final Deque<Map<String, Object>> events = new ArrayDeque<>();
        private long total;

        Probe(Class<?> type) {
            this.type = type;
        }

        synchronized void record(Object event) {
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
        if (result instanceof ProxiedPlayer) {
            return Collections.singletonMap("name", ((ProxiedPlayer) result).getName());
        }
        if (result instanceof ServerInfo) {
            return Collections.singletonMap("name", ((ServerInfo) result).getName());
        }
        if (result instanceof Server) {
            return Collections.singletonMap("name", ((Server) result).getInfo().getName());
        }
        if (result instanceof PendingConnection) {
            return Collections.singletonMap("name", ((PendingConnection) result).getName());
        }
        if (result instanceof byte[]) {
            // A plugin message's payload: its size says it arrived, and the bytes are not JSON.
            return typed("byte[]", ((byte[]) result).length + " bytes");
        }
        if (result instanceof Enum) {
            return typed(result.getClass().getName(), ((Enum<?>) result).name());
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
