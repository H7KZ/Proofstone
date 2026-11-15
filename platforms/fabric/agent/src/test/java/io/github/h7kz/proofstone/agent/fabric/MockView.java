package io.github.h7kz.proofstone.agent.fabric;

import io.github.h7kz.proofstone.agent.game.Fields;
import io.github.h7kz.proofstone.agent.protocol.AgentException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * A server in memory behind {@link GameView}, with one player, {@code Alex}, and a block store. Its "server thread" is a
 * single thread named {@code Server thread}; every view call records the thread it ran on, so a test can prove the agent
 * moved the work there. It answers as the real server does for the golden fixture (golden/README.md), no more.
 */
final class MockView implements GameView {

    static final String THREAD = "Server thread";
    static final String UUID = "8a2c1f0e-6b1d-4f7e-9c3a-2d5e7b9a1c40";

    final ExecutorService serverThread = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, THREAD);
        thread.setDaemon(true);
        return thread;
    });
    final List<String> callThreads = Collections.synchronizedList(new ArrayList<>());
    final List<String> commandsRun = new ArrayList<>();
    /** When true the server thread never runs a task: the agent must answer TIMEOUT. */
    volatile boolean stalled;

    private final Map<String, Map<String, Object>> blocks = new TreeMap<>();
    private final Map<String, Object> inventory = new LinkedHashMap<>();
    private boolean online = true;

    /** The executor the agent gets as the server's task queue. */
    void execute(Runnable task) {
        if (!stalled) {
            serverThread.execute(task);
        }
    }

    void leave() {
        online = false;
    }

    @Override
    public String command(String command) {
        seen();
        if (!"version".equals(command) && !"list".equals(command)) {
            throw Fields.invalid("unknown command " + command);
        }
        commandsRun.add(command);
        return "ran " + command;
    }

    @Override
    public List<String> players() {
        seen();
        return online ? Collections.singletonList("Alex") : Collections.<String>emptyList();
    }

    @Override
    public Map<String, Object> player(String name) {
        seen();
        requireAlex(name);
        Map<String, Object> location = new LinkedHashMap<>();
        location.put("world", "world");
        location.put("x", 0.5);
        location.put("y", 64.0);
        location.put("z", 0.5);
        location.put("yaw", 0.0);
        location.put("pitch", 0.0);
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("name", "Alex");
        state.put("uuid", UUID);
        state.put("level", 0);
        state.put("exp", 0.0);
        state.put("gamemode", "survival");
        state.put("health", 20.0);
        state.put("location", location);
        state.put("inventory", new LinkedHashMap<>(inventory));
        state.put("cursor", null);
        state.put("openWindow", null);
        return state;
    }

    @Override
    public void give(String name, Map<String, Object> item) {
        seen();
        requireAlex(name);
        Map<String, Object> stored = new LinkedHashMap<>(item);
        stored.put("raw", "1 " + item.get("type"));
        inventory.put(String.valueOf(inventory.size()), stored);
    }

    @Override
    public void reset(String name) {
        seen();
        requireAlex(name);
        inventory.clear();
    }

    @Override
    public void patch(String name, Map<String, Object> changes) {
        seen();
        requireAlex(name);
    }

    @Override
    public Map<String, Object> block(String world, int x, int y, int z) {
        seen();
        Map<String, Object> stored = blocks.get(world + "/" + x + "/" + y + "/" + z);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("world", world);
        out.put("x", x);
        out.put("y", y);
        out.put("z", z);
        out.put("type", stored == null ? "minecraft:stone" : stored.get("type"));
        out.put("state", stored == null || stored.get("state") == null ? Collections.emptyMap() : stored.get("state"));
        return out;
    }

    @Override
    public Map<String, Object> setBlock(String world, int x, int y, int z, Map<String, Object> write) {
        seen();
        blocks.put(world + "/" + x + "/" + y + "/" + z, write);
        return block(world, x, y, z);
    }

    @Override
    public List<Map<String, Object>> mods() {
        List<Map<String, Object>> mods = new ArrayList<>();
        mods.add(mod("fabric-api", "0.166.2+26.3"));
        mods.add(mod("proofstone-agent", "0.1.0"));
        return mods;
    }

    @Override
    public Map<String, Object> dataPacks() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("enabled", java.util.Arrays.asList("vanilla", "fabric-convention-tags-v2", "file/x.zip"));
        out.put("available", java.util.Arrays.asList("vanilla", "fabric-convention-tags-v2", "file/x.zip"));
        return out;
    }

    private static Map<String, Object> mod(String id, String version) {
        Map<String, Object> mod = new LinkedHashMap<>();
        mod.put("id", id);
        mod.put("version", version);
        return mod;
    }

    private void seen() {
        callThreads.add(Thread.currentThread().getName());
    }

    private void requireAlex(String name) {
        if (!online || !"Alex".equals(name)) {
            throw new AgentException(404, "PLAYER_NOT_FOUND", "no player named " + name,
                    Collections.singletonMap("name", name));
        }
    }
}
