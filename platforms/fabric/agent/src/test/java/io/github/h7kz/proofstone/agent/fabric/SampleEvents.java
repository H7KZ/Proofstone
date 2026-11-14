package io.github.h7kz.proofstone.agent.fabric;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;

/** Fabric API events built with Fabric API's own factory, so probes run on the real {@link Event} and its phases. */
public final class SampleEvents {

    /** A notification: returns nothing, so a probe may observe it. */
    public interface Joined {
        void onJoin(String name, int count);
    }

    /** A decision: every listener votes, so a probe must refuse it. */
    public interface Allow {
        boolean allow(String name);
    }

    public static final Event<Joined> JOINED = EventFactory.createArrayBacked(Joined.class, listeners -> (name, count) -> {
        for (Joined listener : listeners) {
            listener.onJoin(name, count);
        }
    });

    /** A second notification for the ordering test, so its listeners do not mix with the contract test's. */
    public static final Event<Joined> ORDERED = EventFactory.createArrayBacked(Joined.class, listeners -> (name, count) -> {
        for (Joined listener : listeners) {
            listener.onJoin(name, count);
        }
    });

    public static final Event<Allow> ALLOW = EventFactory.createArrayBacked(Allow.class, listeners -> name -> {
        for (Allow listener : listeners) {
            if (!listener.allow(name)) {
                return false;
            }
        }
        return true;
    });

    /** Not an event at all. */
    public static final String NOT_AN_EVENT = "plain";

    private SampleEvents() {
    }
}
