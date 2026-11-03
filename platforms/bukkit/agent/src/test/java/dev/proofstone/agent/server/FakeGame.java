package dev.proofstone.agent.server;

import dev.proofstone.agent.game.Game;
import dev.proofstone.agent.protocol.AgentException;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Canned game state shaped like the golden cases; records what the server asked for. It keeps one given item
 * in slot 0, so a give followed by a read matches players/read-after-give. It knows one material and rejects
 * every other type with UNKNOWN_KEY before it stores anything, like the real game.
 */
final class FakeGame implements Game {

    private static final String KNOWN_TYPE = "minecraft:diamond_sword";
    private static final String FIXTURE_PLUGIN = "proofstone-fixture";
    private static final String FIXTURE_HOOK = "fixture-reload";

    String lastCommand;
    long lastTimeoutMs = -1;
    Map<String, Object> lastPatch;
    String lastReset;
    String lastReloadHook;
    private Map<String, Object> item;
    /** The fixture's config.yml as the golden cases expect it: greeting is "hi" until a patch. */
    private Map<String, Object> config = fixtureConfig();
    private Map<String, Object> snapshot;

    private static Map<String, Object> fixtureConfig() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("greeting", "hi");
        return config;
    }

    @Override
    public String command(String command, long timeoutMs) {
        lastCommand = command;
        lastTimeoutMs = timeoutMs;
        return "Server version 1.8.8";
    }

    @Override
    public List<String> players(long timeoutMs) {
        lastTimeoutMs = timeoutMs;
        return Collections.singletonList("Alex");
    }

    @Override
    public Map<String, Object> player(String name, long timeoutMs) {
        lastTimeoutMs = timeoutMs;
        requireAlex(name);
        Map<String, Object> location = new LinkedHashMap<>();
        location.put("world", "world");
        location.put("x", 0.5);
        location.put("y", 64.0);
        location.put("z", 0.5);
        location.put("yaw", 0.0);
        location.put("pitch", 0.0);

        Map<String, Object> inventory = new LinkedHashMap<>();
        if (item != null) {
            Map<String, Object> shown = new LinkedHashMap<>(item);
            shown.put("raw", "fake");
            inventory.put("0", shown);
        }

        Map<String, Object> state = new LinkedHashMap<>();
        state.put("name", "Alex");
        state.put("uuid", "8a2c1f0e-6b1d-4f7e-9c3a-2d5e7b9a1c40");
        state.put("level", 0);
        state.put("exp", 0.0);
        state.put("gamemode", "survival");
        state.put("health", 20.0);
        state.put("location", location);
        state.put("inventory", inventory);
        state.put("cursor", null);
        state.put("openWindow", null);
        return state;
    }

    @Override
    public void give(String name, Map<String, Object> newItem, long timeoutMs) {
        lastTimeoutMs = timeoutMs;
        requireAlex(name);
        if (!KNOWN_TYPE.equals(newItem.get("type"))) {
            throw new AgentException(400, "UNKNOWN_KEY", "unknown key " + newItem.get("type"),
                    Collections.singletonMap("key", newItem.get("type")));
        }
        item = new LinkedHashMap<>(newItem);
    }

    @Override
    public void reset(String name, long timeoutMs) {
        lastTimeoutMs = timeoutMs;
        lastReset = name;
        requireAlex(name);
        item = null;
    }

    @Override
    public void patch(String name, Map<String, Object> changes, long timeoutMs) {
        lastTimeoutMs = timeoutMs;
        lastPatch = new LinkedHashMap<>(changes);
        requireAlex(name);
    }

    private static void requireAlex(String name) {
        if (!"Alex".equals(name)) {
            throw new AgentException(404, "PLAYER_NOT_FOUND", "no player named " + name,
                    Collections.singletonMap("name", name));
        }
    }

    @Override
    public Object pluginConfig(String plugin, String path, long timeoutMs) {
        lastTimeoutMs = timeoutMs;
        requirePlugin(plugin);
        Object value = config.get(path);
        if (value == null) {
            throw new AgentException(404, "CONFIG_PATH_NOT_FOUND", "no config value at " + path,
                    Collections.singletonMap("path", path));
        }
        return value;
    }

    @Override
    public void patchPluginConfig(String plugin, String path, Object value, long timeoutMs) {
        lastTimeoutMs = timeoutMs;
        requirePlugin(plugin);
        if (snapshot == null) {
            snapshot = new LinkedHashMap<>(config);
        }
        config.put(path, value);
    }

    @Override
    public void restorePluginConfig(String plugin, long timeoutMs) {
        lastTimeoutMs = timeoutMs;
        requirePlugin(plugin);
        if (snapshot != null) {
            config = new LinkedHashMap<>(snapshot);
            snapshot = null;
        }
    }

    @Override
    public void reloadPlugin(String plugin, String hookCommand, long timeoutMs) {
        lastTimeoutMs = timeoutMs;
        requirePlugin(plugin);
        lastReloadHook = hookCommand;
        if (hookCommand != null && !FIXTURE_HOOK.equals(hookCommand)) {
            throw new AgentException(400, "INVALID_REQUEST",
                    "reload hook is not a registered command: " + hookCommand, null);
        }
    }

    private static void requirePlugin(String name) {
        if (!FIXTURE_PLUGIN.equals(name)) {
            throw new AgentException(404, "PLUGIN_NOT_FOUND", "no plugin named " + name,
                    Collections.singletonMap("id", name));
        }
    }

    static List<String> capabilities() {
        return Arrays.asList("command", "players", "config", "reload");
    }
}
