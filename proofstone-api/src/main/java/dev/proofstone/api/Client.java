package dev.proofstone.api;

import java.util.List;
import java.util.regex.Pattern;

/** A joined player: the client oracle and its actions (test-api spec §4). A fresh one per test. */
public interface Client {

    /** The player name, unique per test. */
    String name();

    /** Sends {@code /<command>}. */
    void command(String commandWithoutSlash);

    /**
     * Waits for a received message whose plain text matches, for the default timeout.
     *
     * @return the matching message text
     * @throws RuntimeException on timeout or disconnect, with the reason
     */
    String awaitMessage(Pattern pattern);

    /** The last position the server synced to this client, or {@code null} before the first sync. */
    Position position();

    /**
     * Waits for a synced position the predicate accepts, for the default timeout. Use it after a teleport, before an
     * action that needs reach.
     *
     * @throws RuntimeException on timeout, with the description and the last position
     */
    Position awaitPosition(String description, java.util.function.Predicate<Position> predicate);

    /**
     * Right-clicks the top face of the block and waits for the window the server opens.
     *
     * @throws RuntimeException when no window opens in the default timeout, with the reason
     */
    Window openBlock(BlockPos pos);

    /** The player inventory (window 0) as last synced: 46 slots, in the protocol's slot order. */
    List<ItemView> inventory();

    /**
     * The experience level the server last synced to this client.
     *
     * @throws RuntimeException when the server has not synced an experience level yet
     */
    int experienceLevel();
}
