package dev.proofstone.agent.bukkit;

import dev.proofstone.agent.protocol.AgentException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.Plugin;

/**
 * Read-only event probes (spec §2, event.json). Each probe is a reflective MONITOR listener that records the
 * event's zero-arg getters and never changes the event (ADR-0002). A probe keeps the last {@value #CAPACITY}
 * events. Subscribe and unsubscribe run on the main thread (the caller wraps them); reads are synchronised.
 */
final class EventProbes {

    static final int CAPACITY = 1000;

    private final Plugin plugin;
    private final TickCounter ticks;
    private final Map<String, Probe> probes = new ConcurrentHashMap<>();
    private int nextId = 1;

    EventProbes(Plugin plugin, TickCounter ticks) {
        this.plugin = plugin;
        this.ticks = ticks;
    }

    /** Loads the class through each plugin's classloader in turn, then the agent's own. */
    String subscribe(String className) {
        Class<? extends Event> type = eventClass(className);
        Probe probe = new Probe(type, ticks);
        String id = "probe-" + nextId++;
        Bukkit.getPluginManager().registerEvent(type, probe.listener, EventPriority.MONITOR,
                probe.executor(), plugin, false);
        probes.put(id, probe);
        return id;
    }

    void unsubscribe(String probeId) {
        Probe probe = probes.remove(probeId);
        if (probe == null) {
            throw new AgentException(404, "PROBE_NOT_FOUND", "no probe named " + probeId,
                    Collections.singletonMap("probeId", probeId));
        }
        HandlerList.unregisterAll(probe.listener);
    }

    /** Captured events of the probe at or after {@code since}. {@code next} is the cursor to pass next time. */
    Map<String, Object> events(String probeId, long since) {
        Probe probe = probes.get(probeId);
        if (probe == null) {
            throw new AgentException(404, "PROBE_NOT_FOUND", "no probe named " + probeId,
                    Collections.singletonMap("probeId", probeId));
        }
        return probe.page(since);
    }

    @SuppressWarnings("unchecked")
    private Class<? extends Event> eventClass(String className) {
        Class<?> found = null;
        for (Plugin owner : Bukkit.getPluginManager().getPlugins()) {
            found = load(className, owner.getClass().getClassLoader());
            if (found != null) {
                break;
            }
        }
        if (found == null) {
            found = load(className, getClass().getClassLoader());
        }
        if (found == null) {
            throw new AgentException(400, "INVALID_REQUEST", "no class named " + className, null);
        }
        if (!Event.class.isAssignableFrom(found)) {
            throw new AgentException(400, "INVALID_REQUEST", className + " is not a Bukkit event", null);
        }
        return (Class<? extends Event>) found;
    }

    private static Class<?> load(String name, ClassLoader loader) {
        try {
            return Class.forName(name, false, loader);
        } catch (ClassNotFoundException | LinkageError e) {
            return null;
        }
    }

    /** One subscription: its listener, its ring buffer and the sequence of the next event. */
    static final class Probe {

        private final Listener listener = new Listener() {
        };
        private final Class<? extends Event> type;
        private final TickCounter ticks;
        private final Deque<Map<String, Object>> events = new ArrayDeque<>();
        private long total;

        Probe(Class<? extends Event> type, TickCounter ticks) {
            this.type = type;
            this.ticks = ticks;
        }

        EventExecutor executor() {
            return new EventExecutor() {
                @Override
                public void execute(Listener ignored, Event event) {
                    record(event);
                }
            };
        }

        private synchronized void record(Event event) {
            Map<String, Object> captured = new LinkedHashMap<>();
            captured.put("class", type.getName());
            captured.put("tick", ticks.current());
            captured.put("thread", Thread.currentThread().getName());
            captured.put("fields", fields(event));
            events.addLast(captured);
            total++;
            if (events.size() > CAPACITY) {
                events.removeFirst();
            }
        }

        synchronized Map<String, Object> page(long since) {
            long first = total - events.size();
            List<Object> out = new ArrayList<>();
            long seq = first;
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

    /**
     * The event's zero-arg public getters, by method name (golden events/poll keeps the {@code get} prefix). Getters
     * declared by Bukkit's {@code Event} base class and {@code getHandlers} are plugin plumbing, not event state, so
     * they are left out.
     */
    static Map<String, Object> fields(Event event) {
        List<Method> getters = new ArrayList<>();
        for (Method method : event.getClass().getMethods()) {
            if (isGetter(method)) {
                getters.add(method);
            }
        }
        Collections.sort(getters, new Comparator<Method>() {
            @Override
            public int compare(Method a, Method b) {
                return a.getName().compareTo(b.getName());
            }
        });
        Map<String, Object> out = new LinkedHashMap<>();
        for (Method getter : getters) {
            out.put(getter.getName(), value(getter, event));
        }
        return out;
    }

    private static boolean isGetter(Method method) {
        String name = method.getName();
        return method.getParameterTypes().length == 0
                && !Modifier.isStatic(method.getModifiers())
                && method.getReturnType() != void.class
                && name.startsWith("get")
                && !"getClass".equals(name)
                && !"getHandlers".equals(name)
                && method.getDeclaringClass() != Event.class
                && method.getDeclaringClass() != Object.class;
    }

    /** A getter's value as JSON-ready data. A getter that throws is recorded as an {@code error} typed value. */
    private static Object value(Method getter, Event event) {
        Object result;
        try {
            result = getter.invoke(event);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return typed("error", String.valueOf(e));
        }
        if (result == null || result instanceof String || result instanceof Boolean) {
            return result;
        }
        if (result instanceof Number) {
            return result;
        }
        if (result instanceof Player) {
            return Collections.singletonMap("name", ((Player) result).getName());
        }
        if (result instanceof ItemStack) {
            return BukkitGame.stackOrNull((ItemStack) result);
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
