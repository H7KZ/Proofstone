package dev.proofstone.agent.server;

import dev.proofstone.agent.game.Game;
import dev.proofstone.agent.protocol.AgentException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
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

    FakeGame() {
        Map<String, Object> stone = new LinkedHashMap<>();
        stone.put("type", "minecraft:stone");
        stone.put("state", Collections.emptyMap());
        blocks.put(key("world", 0, 63, 0), stone);
    }

    String lastCommand;
    long lastTimeoutMs = -1;
    Map<String, Object> lastPatch;
    String lastReset;
    private Map<String, Object> item;
    /** Blocks by {@code world x,y,z}; the fixture stone at (0, 63, 0) matches golden/world/block-get. */
    private final Map<String, Map<String, Object>> blocks = new HashMap<>();
    private String probeClass;
    private long globalTicks;

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
    public Map<String, Object> block(String world, int x, int y, int z, long timeoutMs) {
        lastTimeoutMs = timeoutMs;
        Map<String, Object> block = blocks.get(key(world, x, y, z));
        if (block == null) {
            block = new LinkedHashMap<>();
            block.put("type", "minecraft:air");
            block.put("state", Collections.emptyMap());
        }
        return withPosition(world, x, y, z, block);
    }

    @Override
    public Map<String, Object> setBlock(String world, int x, int y, int z, Map<String, Object> write,
            long timeoutMs) {
        lastTimeoutMs = timeoutMs;
        if (!"minecraft:anvil".equals(write.get("type")) && !"minecraft:stone".equals(write.get("type"))) {
            throw new AgentException(400, "UNKNOWN_KEY", "unknown key " + write.get("type"),
                    Collections.singletonMap("key", write.get("type")));
        }
        blocks.put(key(world, x, y, z), new LinkedHashMap<>(write));
        return block(world, x, y, z, timeoutMs);
    }

    @Override
    public String subscribe(String className, long timeoutMs) {
        lastTimeoutMs = timeoutMs;
        probeClass = className;
        return "probe-1";
    }

    /** One canned PlayerJoinEvent once a probe exists (golden/events/poll). */
    @Override
    public Map<String, Object> events(String probeId, long since, long timeoutMs) {
        lastTimeoutMs = timeoutMs;
        if (probeClass == null || !"probe-1".equals(probeId)) {
            throw new AgentException(404, "PROBE_NOT_FOUND", "no probe named " + probeId,
                    Collections.singletonMap("probeId", probeId));
        }
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("getPlayer", Collections.singletonMap("name", "Alex"));
        fields.put("getJoinMessage", "Alex joined the game");
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("class", probeClass);
        event.put("tick", 0);
        event.put("thread", "Server thread");
        event.put("fields", fields);
        Map<String, Object> page = new LinkedHashMap<>();
        page.put("events", new ArrayList<Object>(Collections.singletonList(event)));
        page.put("next", 1);
        return page;
    }

    @Override
    public void unsubscribe(String probeId, long timeoutMs) {
        lastTimeoutMs = timeoutMs;
        if (probeClass == null || !"probe-1".equals(probeId)) {
            throw new AgentException(404, "PROBE_NOT_FOUND", "no probe named " + probeId,
                    Collections.singletonMap("probeId", probeId));
        }
        probeClass = null;
    }

    /** One canned WARN with a stack trace (golden/log/read). */
    @Override
    public Map<String, Object> log(long since, String minLevel, long timeoutMs) {
        lastTimeoutMs = timeoutMs;
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("time", "2026-11-03T12:00:00Z");
        record.put("level", "WARN");
        record.put("logger", "Proofstone");
        record.put("message", "canned");
        record.put("thrown", "java.lang.IllegalStateException: canned\n\tat Fake.log(FakeGame.java)");
        Map<String, Object> page = new LinkedHashMap<>();
        page.put("records", new ArrayList<Object>(Collections.singletonList(record)));
        page.put("next", 1);
        return page;
    }

    @Override
    public Map<String, Object> ticks(long timeoutMs) {
        lastTimeoutMs = timeoutMs;
        return Collections.<String, Object>singletonMap("global", globalTicks);
    }

    @Override
    public Map<String, Object> awaitTicks(long n, long timeoutMs) {
        lastTimeoutMs = timeoutMs;
        globalTicks += n;
        return Collections.<String, Object>singletonMap("global", globalTicks);
    }

    private static String key(String world, int x, int y, int z) {
        return world + " " + x + "," + y + "," + z;
    }

    private static Map<String, Object> withPosition(String world, int x, int y, int z, Map<String, Object> block) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("world", world);
        out.put("x", x);
        out.put("y", y);
        out.put("z", z);
        out.putAll(block);
        return out;
    }

    static List<String> capabilities() {
        return Arrays.asList("command", "players", "world", "events", "log", "ticks");
    }
}
