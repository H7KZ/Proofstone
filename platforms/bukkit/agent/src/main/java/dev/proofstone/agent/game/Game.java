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
}
