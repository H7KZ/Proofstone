package io.github.h7kz.proofstone.agent.neoforge;

import io.github.h7kz.proofstone.agent.protocol.AgentException;
import io.github.h7kz.proofstone.agent.vanilla.Values;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;

/**
 * Read-only event probes (spec section 2, event.json) on NeoForge's game bus. The subscribed name is the event's class
 * name (for example {@code net.neoforged.neoforge.event.entity.player.PlayerEvent$PlayerLoggedInEvent}).
 *
 * <p>Observe only (AGENTS.md section 3, ADR-0017 section 5): a probe listens at {@link EventPriority#LOWEST}, so it runs
 * after every listener of a higher priority, and it receives cancelled events too, as a Bukkit MONITOR probe does. It
 * never cancels: the agent's code holds no call that could (ObserveOnlyTest scans it). Fields are the event's zero-arg
 * public {@code get*} getters by method name, as the Bukkit agent records them; getters of the bus's own {@link Event}
 * are left out. Each probe keeps the last {@value #CAPACITY} events. An unsubscribed probe leaves the bus.
 */
final class NeoForgeEventProbes {

    static final int CAPACITY = 1000;

    private final IEventBus bus;
    private final LongSupplier tick;
    private final Map<String, Probe> probes = new ConcurrentHashMap<>();
    private final AtomicInteger nextId = new AtomicInteger(1);

    /**
     * @param bus  the game bus ({@code NeoForge.EVENT_BUS} on a server)
     * @param tick the current global tick, recorded with each event
     */
    NeoForgeEventProbes(IEventBus bus, LongSupplier tick) {
        this.bus = bus;
        this.tick = tick;
    }

    /** Registers {@code listener} last on {@code bus}: lowest priority, cancelled events included. */
    static <T extends Event> void registerLast(IEventBus bus, Class<T> type, Consumer<T> listener) {
        bus.addListener(EventPriority.LOWEST, true, type, listener);
    }

    String subscribe(String name) {
        @SuppressWarnings("unchecked")
        Class<Event> type = (Class<Event>) eventType(name);
        Probe probe = new Probe(type);
        try {
            registerLast(bus, type, probe);
        } catch (IllegalArgumentException e) {
            // The bus refuses some types (an abstract event, a mod-bus event on the game bus): say so, never a dead probe.
            throw invalid(name + " cannot be observed on the game bus: " + e.getMessage());
        }
        String id = "probe-" + nextId.getAndIncrement();
        probes.put(id, probe);
        return id;
    }

    Map<String, Object> events(String probeId, long since) {
        return probe(probeId).page(since);
    }

    void unsubscribe(String probeId) {
        Probe probe = probe(probeId);
        probes.remove(probeId);
        bus.unregister(probe);
    }

    private Probe probe(String probeId) {
        Probe probe = probes.get(probeId);
        if (probe == null) {
            throw new AgentException(404, "PROBE_NOT_FOUND", "no probe named " + probeId,
                    Collections.singletonMap("probeId", probeId));
        }
        return probe;
    }

    private static Class<? extends Event> eventType(String name) {
        Class<?> type;
        try {
            type = Class.forName(name, false, NeoForgeEventProbes.class.getClassLoader());
        } catch (ClassNotFoundException | LinkageError e) {
            throw invalid("no event named " + name + " (" + e + ")");
        }
        if (!Event.class.isAssignableFrom(type)) {
            throw invalid(name + " is not a NeoForge event");
        }
        if (Modifier.isAbstract(type.getModifiers())) {
            throw invalid(name + " is abstract: name the concrete event");
        }
        return type.asSubclass(Event.class);
    }

    private static AgentException invalid(String message) {
        return new AgentException(400, "INVALID_REQUEST", message, null);
    }

    /** The event's zero-arg public getters, sorted by name. A getter that throws is one field's error, not a lost event. */
    private static Map<String, Object> fields(Event event) {
        Map<String, Object> out = new TreeMap<>();
        for (Method method : event.getClass().getMethods()) {
            if (!isGetter(method) || out.containsKey(method.getName())) {
                continue;
            }
            Object value;
            try {
                value = Values.json(method.invoke(event));
            } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
                Throwable cause = e instanceof InvocationTargetException && e.getCause() != null ? e.getCause() : e;
                value = Values.typed("error", String.valueOf(cause));
            }
            out.put(method.getName(), value);
        }
        return out;
    }

    private static boolean isGetter(Method method) {
        return method.getParameterCount() == 0
                && !Modifier.isStatic(method.getModifiers())
                && !method.isBridge()
                && method.getReturnType() != void.class
                && method.getName().startsWith("get")
                && !"getClass".equals(method.getName())
                && method.getDeclaringClass() != Event.class
                && method.getDeclaringClass() != Object.class;
    }

    /** One subscription: the listener itself (so the bus can unregister it), its ring buffer and its cursor. */
    private final class Probe implements Consumer<Event> {

        private final Class<? extends Event> type;
        private final Deque<Map<String, Object>> events = new ArrayDeque<>();
        private long total;

        Probe(Class<? extends Event> type) {
            this.type = type;
        }

        /** Reads the event inline: its state is only safe to read during its own dispatch. */
        @Override
        public void accept(Event event) {
            Map<String, Object> captured = new LinkedHashMap<>();
            captured.put("class", type.getName());
            captured.put("tick", tick.getAsLong());
            captured.put("thread", Thread.currentThread().getName());
            captured.put("fields", fields(event));
            synchronized (this) {
                events.addLast(captured);
                total++;
                if (events.size() > CAPACITY) {
                    events.removeFirst();
                }
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
}
