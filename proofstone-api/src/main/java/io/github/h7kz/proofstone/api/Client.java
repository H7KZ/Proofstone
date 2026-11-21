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

    /**
     * The resource packs the server pushed to this client, in order, in the configuration phase and in play, with what
     * the client answered and sent (ADR-0017 section 7). Packs a plugin pushes are recorded the same way.
     *
     * <p>Additive (T-M8-10). The default throws, so a custom {@code Client} fails loudly instead of passing.
     */
    default List<ResourcePackPush> resourcePacks() {
        throw new UnsupportedOperationException("this Client implementation does not record resource packs; override "
                + "resourcePacks");
    }

    /**
     * Places the held item (the main hand's selected hotbar slot) against {@code face} of the block at {@code against}, as a
     * player right-clicking it: the server fires the real {@code BlockPlaceEvent} and the new block lands on the
     * neighbouring position. Returns once the server has answered the action, so a server read afterwards sees the result;
     * the server may still refuse (reach, protection, a cancelling plugin), which the test reads from the server. A player
     * who does not sneak opens an anvil, chest or crafting table instead of placing against it, and so does this call: place
     * against plain blocks.
     *
     * <p>Additive (T-M11-04). The default throws, so a custom {@code Client} fails loudly instead of passing. A driver
     * that cannot place blocks (Bedrock, the reference Mineflayer driver) fails the same way.
     *
     * @throws UnsupportedOperationException when this client or its driver cannot place blocks (every driver, one type)
     * @throws RuntimeException when the server never answers, with the reason
     */
    default void placeBlock(BlockPos against, Face face) {
        throw new UnsupportedOperationException("this Client implementation cannot place blocks; override placeBlock");
    }

    /**
     * Breaks the block at {@code pos} as a player digging it: start, then finish at once, so the server fires the real
     * {@code BlockBreakEvent}. The server's own rules decide: the block breaks at that instant only in creative or when its
     * break time is zero (for example with the {@code minecraft:block_break_speed} attribute raised); otherwise the server
     * rejects the finish and the block stays. Returns once the server has answered.
     *
     * <p>Additive (T-M11-04). The default throws, so a custom {@code Client} fails loudly instead of passing. No abort-digging
     * follows a rejected finish (T-M11-13): a vanilla client never sends one after a finish.
     *
     * @throws UnsupportedOperationException when this client or its driver cannot break blocks (every driver, one type)
     * @throws RuntimeException when the server never answers, with the reason
     */
    default void breakBlock(BlockPos pos) {
        throw new UnsupportedOperationException("this Client implementation cannot break blocks; override breakBlock");
    }
}
