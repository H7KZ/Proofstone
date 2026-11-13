package io.github.h7kz.proofstone.api;

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

    /**
     * Moves this client to another backend of a topology (a server switch, ADR-0016 section 5): it types
     * {@code /server <name>} as a player would, waits for the client to see the new server's join, then waits for the
     * agents to agree: the proxy agent says the player is on {@code name}, that backend lists the player and the backend
     * it left does not. It is not the vanilla transfer packet. On return the client's views are the new server's.
     *
     * <p>Additive (T-M7-10). The default throws, so a custom {@code Client} fails loudly instead of passing.
     *
     * @throws IllegalArgumentException before anything is sent, when {@code name} is not a backend of the topology or is
     *     the backend the client is on
     * @throws IllegalStateException on a single-server cell, which has no backends to switch between
     * @throws DisagreeException when the client saw the switch but an agent says otherwise, or the agents disagree; the
     *     message names every agent and its value
     * @throws AssertionError starting {@code FAIL} when no oracle shows the client on {@code name}, or
     *     {@code ORACLE MISSING} when an agent never answers
     * @throws RuntimeException on a switch timeout, naming the stalled stage and the hop
     */
    default void transferTo(String name) {
        throw new UnsupportedOperationException("this Client implementation cannot switch servers; override transferTo");
    }
}
