package dev.proofstone.agent.game;

import dev.proofstone.agent.protocol.AgentException;
import java.util.List;
import java.util.Map;

/**
 * Server state the endpoints read and command. Implementations run work on the thread that owns the state
 * and block until it is done, or throw {@link AgentException} on TIMEOUT or on a failure. The values are
 * JSON-ready trees (maps, lists, strings, numbers, booleans, null).
 */
public interface Game {

    /** Runs a console-style command as a capturing sender. Returns the captured output (best-effort). */
    String command(String command, long timeoutMs);

    /** Names of the online players. */
    List<String> players(long timeoutMs);

    /** {@code PlayerState} (spec §3). Throws {@code PLAYER_NOT_FOUND} when the player is not online. */
    Map<String, Object> player(String name, long timeoutMs);

    /**
     * Adds an {@code Item} (spec §3, test API §6) to the player's first empty storage slot. Throws
     * {@code UNKNOWN_KEY} before anything changes when a material or enchantment is unknown.
     */
    void give(String name, Map<String, Object> item, long timeoutMs);

    /** Resets the player to the fixture state (spec §2). Idempotent. */
    void reset(String name, long timeoutMs);

    /** Applies the present fields of a {@code PATCH} body (spec §2) all at once, or none of them. */
    void patch(String name, Map<String, Object> changes, long timeoutMs);
}
