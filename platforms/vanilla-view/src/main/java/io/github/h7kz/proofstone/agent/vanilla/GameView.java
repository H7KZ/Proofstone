package io.github.h7kz.proofstone.agent.vanilla;

import java.util.List;
import java.util.Map;

/**
 * The server state the endpoints read and change, as JSON-ready trees (spec section 3). Every method runs on the server
 * thread: each loader agent's game class puts it there. {@link MinecraftView} is the real server; the contract tests use
 * {@code MockView}, so the wire contract is checked without a game, and the real server by the agents' integration tests.
 * Shared by the Fabric and NeoForge agents (T-M8-15); what only one loader can answer lives in that agent.
 */
public interface GameView {

    /** Runs a console command as a capturing source; the reply is the captured output. Unknown command: INVALID_REQUEST. */
    String command(String command);

    /** Names of the online players, in the server's order. */
    List<String> players();

    /** {@code PlayerState} (player-state.json). Throws {@code PLAYER_NOT_FOUND}. */
    Map<String, Object> player(String name);

    /** Adds an item to the first empty storage slot; validates everything first (UNKNOWN_KEY, INVALID_REQUEST). */
    void give(String name, Map<String, Object> item);

    /** The fixture state (spec section 2): no window, empty inventory and cursor, level 0, survival, full health, spawn. */
    void reset(String name);

    /** Level, exp, gamemode, location, op: all validated, then all applied. */
    void patch(String name, Map<String, Object> changes);

    /** {@code Block} (block.json). An unknown world is INVALID_REQUEST. */
    Map<String, Object> block(String world, int x, int y, int z);

    /** Writes {@code block.json#/$defs/write} and reads it back. An unknown type or state is UNKNOWN_KEY, nothing changed. */
    Map<String, Object> setBlock(String world, int x, int y, int z, Map<String, Object> write);

    /** The mods loaded from the server's mods folder, sorted by id (mods.json). */
    List<Map<String, Object>> mods();
}
