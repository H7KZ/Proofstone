package io.github.h7kz.proofstone.fixture;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * An event class that belongs to a plugin other than the agent, as Disenchantment's events do. It is never fired: the agent
 * contract test only subscribes a probe to it, which loads the class (T-M6-111).
 */
public final class FixtureEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
