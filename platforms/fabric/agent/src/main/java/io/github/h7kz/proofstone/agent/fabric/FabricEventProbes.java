package io.github.h7kz.proofstone.agent.fabric;

import io.github.h7kz.proofstone.agent.protocol.AgentException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongSupplier;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.resources.Identifier;

/**
 * Read-only event probes (spec section 2, event.json) on Fabric API callbacks. The subscribed name is the callback's
 * holder field, {@code <class>.<FIELD>} (for example {@code net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.JOIN}).
 *
 * <p>Observe only (AGENTS.md section 3, ADR-0017 section 5): a probe is a listener in the agent's own phase, ordered
 * after the default phase, so it runs after every listener that did not ask for a later phase. Only callbacks whose
 * method returns {@code void} are accepted: a callback with a result (an {@code ALLOW_*} or an {@code InteractionResult})
 * lets a listener decide, and a probe must not. Arguments are recorded as {@code arg0..argN} (Fabric API ships no
 * parameter names) and nothing is called on them but reads. Fabric API events cannot unregister a listener, so an
 * unsubscribed probe stays registered and records nothing. Each probe keeps the last {@value #CAPACITY} events.
 */
final class FabricEventProbes {

    static final int CAPACITY = 1000;
    /** The agent's listener phase, ordered after {@link Event#DEFAULT_PHASE} on every event it joins. */
    static final Identifier PHASE = Identifier.fromNamespaceAndPath("proofstone", "observe");

    private final LongSupplier tick;
    private final Map<String, Probe> probes = new ConcurrentHashMap<>();
    private final AtomicInteger nextId = new AtomicInteger(1);

    /** @param tick the current global tick, recorded with each event */
    FabricEventProbes(LongSupplier tick) {
        this.tick = tick;
    }

    /** Registers {@code listener} last on {@code event}: the agent's phase, after the default one. */
    static <T> void registerLast(Event<T> event, T listener) {
        event.addPhaseOrdering(Event.DEFAULT_PHASE, PHASE);
        event.register(PHASE, listener);
    }

    String subscribe(String name) {
        Event<Object> event = event(name);
        Probe probe = new Probe(name);
        registerLast(event, listener(callbackType(name), probe));
        String id = "probe-" + nextId.getAndIncrement();
        probes.put(id, probe);
        return id;
    }

    Map<String, Object> events(String probeId, long since) {
        return probe(probeId).page(since);
    }

    void unsubscribe(String probeId) {
        Probe probe = probe(probeId);
        probe.active = false;
        probes.remove(probeId);
    }

    private Probe probe(String probeId) {
        Probe probe = probes.get(probeId);
        if (probe == null) {
            throw new AgentException(404, "PROBE_NOT_FOUND", "no probe named " + probeId,
                    Collections.singletonMap("probeId", probeId));
        }
        return probe;
    }

    @SuppressWarnings("unchecked")
    private static Event<Object> event(String name) {
        Object value;
        try {
            value = holderField(name).get(null);
        } catch (IllegalAccessException e) {
            throw invalid(name + " is not readable: " + e);
        }
        if (!(value instanceof Event)) {
            throw invalid(name + " is not a Fabric API event");
        }
        return (Event<Object>) value;
    }

    /** The callback interface: the type argument of the holder field's {@code Event<T>}, with one void method. */
    private static Class<?> callbackType(String name) {
        Type type = holderField(name).getGenericType();
        if (!(type instanceof ParameterizedType)
                || !(((ParameterizedType) type).getActualTypeArguments()[0] instanceof Class)) {
            throw invalid(name + " does not name its callback type");
        }
        Class<?> callback = (Class<?>) ((ParameterizedType) type).getActualTypeArguments()[0];
        Method method = callbackMethod(callback);
        if (method.getReturnType() != void.class) {
            throw invalid(name + " returns " + method.getReturnType().getName()
                    + ": a probe never decides a result (observe only), so only void callbacks can be observed");
        }
        return callback;
    }

    private static Method callbackMethod(Class<?> callback) {
        Method found = null;
        for (Method method : callback.getMethods()) {
            if (Modifier.isAbstract(method.getModifiers())) {
                if (found != null) {
                    throw invalid(callback.getName() + " has more than one abstract method");
                }
                found = method;
            }
        }
        if (!callback.isInterface() || found == null) {
            throw invalid(callback.getName() + " is not a callback interface");
        }
        return found;
    }

    private static Field holderField(String name) {
        int dot = name.lastIndexOf('.');
        if (dot <= 0) {
            throw invalid("name the event as <class>.<FIELD>, got " + name);
        }
        try {
            Class<?> holder = Class.forName(name.substring(0, dot), true, FabricEventProbes.class.getClassLoader());
            Field field = holder.getField(name.substring(dot + 1));
            if (!Modifier.isStatic(field.getModifiers())) {
                throw invalid(name + " is not a static field");
            }
            return field;
        } catch (ClassNotFoundException | NoSuchFieldException | LinkageError e) {
            throw invalid("no event named " + name + " (" + e + ")");
        }
    }

    private Object listener(Class<?> callback, Probe probe) {
        InvocationHandler handler = (proxy, method, args) -> {
            if (method.getDeclaringClass() == Object.class) {
                return objectMethod(proxy, method, args);
            }
            probe.record(args == null ? new Object[0] : args);
            return null;
        };
        return Proxy.newProxyInstance(callback.getClassLoader(), new Class<?>[] {callback}, handler);
    }

    private static Object objectMethod(Object proxy, Method method, Object[] args) {
        switch (method.getName()) {
            case "equals":
                return proxy == args[0];
            case "hashCode":
                return System.identityHashCode(proxy);
            default:
                return "proofstone probe";
        }
    }

    private static AgentException invalid(String message) {
        return new AgentException(400, "INVALID_REQUEST", message, null);
    }

    /** One subscription: its ring buffer and the sequence of the next event. */
    private final class Probe {

        private final String name;
        private final Deque<Map<String, Object>> events = new ArrayDeque<>();
        private long total;
        private volatile boolean active = true;

        Probe(String name) {
            this.name = name;
        }

        /** Reads the arguments inline: a callback's arguments are only safe to read during its own dispatch. */
        void record(Object[] args) {
            if (!active) {
                return;
            }
            Map<String, Object> fields = new LinkedHashMap<>();
            for (int i = 0; i < args.length; i++) {
                fields.put("arg" + i, Values.json(args[i]));
            }
            Map<String, Object> captured = new LinkedHashMap<>();
            captured.put("class", name);
            captured.put("tick", tick.getAsLong());
            captured.put("thread", Thread.currentThread().getName());
            captured.put("fields", fields);
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
