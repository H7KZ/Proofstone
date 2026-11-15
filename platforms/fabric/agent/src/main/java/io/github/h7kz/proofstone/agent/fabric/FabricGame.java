package io.github.h7kz.proofstone.agent.fabric;

import io.github.h7kz.proofstone.agent.game.Game;
import io.github.h7kz.proofstone.agent.log.LogBuffer;
import io.github.h7kz.proofstone.agent.protocol.AgentException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link Game} for Fabric: every game read and write runs on the server thread through {@link ServerThread}; probes, the
 * log and the tick counter are thread-safe and answer from the HTTP thread. A Fabric server has no plugins, so the plugin
 * endpoints are {@code NOT_FOUND} and the snapshot's plugin list is empty; {@code /v1/mods} lists what it loaded.
 */
final class FabricGame implements Game {

    private final ServerThread thread;
    private final GameView view;
    private final FabricEventProbes probes;
    private final LogBuffer logs;
    private final TickCounter ticks;

    FabricGame(ServerThread thread, GameView view, FabricEventProbes probes, LogBuffer logs, TickCounter ticks) {
        this.thread = thread;
        this.view = view;
        this.probes = probes;
        this.logs = logs;
        this.ticks = ticks;
    }

    @Override
    public String command(String command, long timeoutMs) {
        return thread.call(() -> view.command(command), timeoutMs);
    }

    @Override
    public List<String> players(long timeoutMs) {
        return thread.call(view::players, timeoutMs);
    }

    @Override
    public Map<String, Object> player(String name, long timeoutMs) {
        return thread.call(() -> view.player(name), timeoutMs);
    }

    @Override
    public void give(String name, Map<String, Object> item, long timeoutMs) {
        thread.call(() -> {
            view.give(name, item);
            return null;
        }, timeoutMs);
    }

    @Override
    public void reset(String name, long timeoutMs) {
        thread.call(() -> {
            view.reset(name);
            return null;
        }, timeoutMs);
    }

    @Override
    public void patch(String name, Map<String, Object> changes, long timeoutMs) {
        thread.call(() -> {
            view.patch(name, changes);
            return null;
        }, timeoutMs);
    }

    @Override
    public Map<String, Object> block(String world, int x, int y, int z, long timeoutMs) {
        return thread.call(() -> view.block(world, x, y, z), timeoutMs);
    }

    @Override
    public Map<String, Object> setBlock(String world, int x, int y, int z, Map<String, Object> write, long timeoutMs) {
        return thread.call(() -> view.setBlock(world, x, y, z, write), timeoutMs);
    }

    @Override
    public String subscribe(String className, long timeoutMs) {
        return probes.subscribe(className);
    }

    @Override
    public Map<String, Object> events(String probeId, long since, long timeoutMs) {
        return probes.events(probeId, since);
    }

    @Override
    public void unsubscribe(String probeId, long timeoutMs) {
        probes.unsubscribe(probeId);
    }

    @Override
    public Map<String, Object> log(long since, String minLevel, long timeoutMs) {
        return logs.read(since, minLevel);
    }

    @Override
    public Map<String, Object> ticks(long timeoutMs) {
        return Collections.<String, Object>singletonMap("global", ticks.current());
    }

    @Override
    public Map<String, Object> awaitTicks(long n, long timeoutMs) {
        return Collections.<String, Object>singletonMap("global", ticks.await(n, timeoutMs));
    }

    @Override
    public Object pluginConfig(String plugin, String path, long timeoutMs) {
        throw noPlugins();
    }

    @Override
    public void patchPluginConfig(String plugin, String path, Object value, long timeoutMs) {
        throw noPlugins();
    }

    @Override
    public void restorePluginConfig(String plugin, long timeoutMs) {
        throw noPlugins();
    }

    @Override
    public void reloadPlugin(String plugin, String hookCommand, long timeoutMs) {
        throw noPlugins();
    }

    @Override
    public List<Map<String, Object>> mods(long timeoutMs) {
        return view.mods();
    }

    @Override
    public Map<String, Object> dataPacks(long timeoutMs) {
        return thread.call(view::dataPacks, timeoutMs);
    }

    /** snapshot.json: every online player, sorted by name, read in one pass on the server thread. No plugins. */
    @Override
    public Map<String, Object> snapshot(long timeoutMs) {
        return thread.call(() -> {
            List<String> names = new ArrayList<>(view.players());
            Collections.sort(names);
            List<Object> players = new ArrayList<>();
            for (String name : names) {
                players.add(view.player(name));
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("players", players);
            out.put("plugins", Collections.emptyList());
            return out;
        }, timeoutMs);
    }

    private static AgentException noPlugins() {
        return new AgentException(404, "NOT_FOUND", "a Fabric server has no plugins; GET /v1/mods lists its mods", null);
    }
}
