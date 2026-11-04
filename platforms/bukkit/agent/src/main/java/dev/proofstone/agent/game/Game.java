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

    /** {@code Block} (block.json) at a position. Throws {@code INVALID_REQUEST} for an unknown world. */
    Map<String, Object> block(String world, int x, int y, int z, long timeoutMs);

    /**
     * Sets the block to {@code write} ({@code block.json#/$defs/write}) and returns it as read back. An unknown
     * type or state is {@code UNKNOWN_KEY} before anything changes.
     */
    Map<String, Object> setBlock(String world, int x, int y, int z, Map<String, Object> write, long timeoutMs);

    /** Subscribes a read-only MONITOR probe to the event class and returns its {@code probeId}. */
    String subscribe(String className, long timeoutMs);

    /** Captured events of the probe at or after {@code since}, as {@code {events, next}} (event.json). */
    Map<String, Object> events(String probeId, long since, long timeoutMs);

    /** Removes the probe and its listener. Throws {@code PROBE_NOT_FOUND} for an unknown id. */
    void unsubscribe(String probeId, long timeoutMs);

    /** Captured log records since the cursor at or above {@code minLevel}, as {@code {records, next}}. */
    Map<String, Object> log(long since, String minLevel, long timeoutMs);

    /** The global tick counter, as {@code {global}} (ticks.json). */
    Map<String, Object> ticks(long timeoutMs);

    /** Waits until {@code n} more global ticks have passed, then returns {@code {global}}. Throws TIMEOUT. */
    Map<String, Object> awaitTicks(long n, long timeoutMs);

    /**
     * The value at a dotted config path of a plugin (spec §2). Throws {@code PLUGIN_NOT_FOUND} for an unknown
     * plugin and {@code CONFIG_PATH_NOT_FOUND} for a path with no value.
     */
    Object pluginConfig(String plugin, String path, long timeoutMs);

    /**
     * Sets a dotted config path and saves the plugin's {@code config.yml}. The first patch since the last restore
     * snapshots the file first (ADR-0002).
     */
    void patchPluginConfig(String plugin, String path, Object value, long timeoutMs);

    /** Writes the snapshot back and reloads the plugin. Does nothing when nothing was patched. */
    void restorePluginConfig(String plugin, long timeoutMs);

    /** Reloads one plugin: runs {@code hookCommand} as the console when given, else disables and enables it. */
    void reloadPlugin(String plugin, String hookCommand, long timeoutMs);
}
