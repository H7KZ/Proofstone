package io.github.h7kz.proofstone.api;

import java.util.function.Predicate;

/** Setup and the server oracle for one player (test-api spec section 3). Calls block until the agent confirms. */
public interface PlayerHandle {

    /** The player name. */
    String name();

    /** Clears the inventory and cursor, sets experience to 0 and returns the player to the fixture spot. */
    PlayerHandle reset();

    /** Sets the experience level. */
    PlayerHandle level(int level);

    /** Puts the item into the inventory. */
    PlayerHandle give(Item item);

    /** The server's current view of the player. */
    PlayerState state();

    /**
     * Waits for a state the predicate accepts, for the default timeout.
     *
     * @param description what is awaited, for the failure message
     * @throws RuntimeException on timeout, with the description and the last state
     */
    PlayerState awaitState(String description, Predicate<PlayerState> predicate);
}
