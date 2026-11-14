package io.github.h7kz.proofstone.agent.neoforge;

import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

/** Events on NeoForge's own {@link Event} base, so probes run on the real bus with its priorities and cancellation. */
public final class SampleEvents {

    private SampleEvents() {
    }

    /** A notification with two getters, as a NeoForge event carries its state. */
    public static class Joined extends Event {
        private final String name;
        private final int count;

        public Joined(String name, int count) {
            this.name = name;
            this.count = count;
        }

        public String getName() {
            return name;
        }

        public int getCount() {
            return count;
        }
    }

    /** A second event for the ordering tests, so their listeners do not mix with the contract test's. */
    public static class Ordered extends Event {
    }

    /** An event other listeners may cancel: a probe must see it either way and never change it. */
    public static class Cancellable extends Event implements ICancellableEvent {
    }

    /** A family base: the bus refuses listeners on an abstract event, so a probe cannot name one. */
    public abstract static class Family extends Event {
    }

    /** Not an event at all. */
    public static final class NotAnEvent {
    }
}
