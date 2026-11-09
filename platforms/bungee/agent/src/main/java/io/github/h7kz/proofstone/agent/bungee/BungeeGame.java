package io.github.h7kz.proofstone.agent.bungee;

import io.github.h7kz.proofstone.agent.game.Game;
import io.github.h7kz.proofstone.agent.log.LogBuffer;
import io.github.h7kz.proofstone.agent.protocol.AgentException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.connection.Server;

/**
 * What a proxy agent serves: the proxy's backends, the players on it, a console-style command, the log and the event
 * probes (spec §2). It only reads, and it runs the one command the harness asks for; it registers nothing. The
 * game-state endpoints (world, give, ticks, plugin config, snapshot) are not served by a proxy and answer 404.
 */
final class BungeeGame implements Game {

    private static final Comparator<ProxiedPlayer> BY_NAME = Comparator.comparing(ProxiedPlayer::getName);

    private final ProxyServer proxy;
    private final ProxyThread thread;
    private final EventProbes probes;
    private final LogBuffer logs;
    private final PreviousServers previous;

    BungeeGame(ProxyServer proxy, ProxyThread thread, EventProbes probes, LogBuffer logs, PreviousServers previous) {
        this.proxy = proxy;
        this.thread = thread;
        this.probes = probes;
        this.logs = logs;
        this.previous = previous;
    }

    @Override
    public String command(String command, long timeoutMs) {
        CapturingSender sender = new CapturingSender();
        // dispatchCommand runs the command on this scheduler thread and returns false only for a command it does not know.
        // A syntax error or a refusal is the command's own output, not a failed dispatch.
        boolean handled = thread.call(() -> proxy.getPluginManager().dispatchCommand(sender, command), timeoutMs);
        if (!handled) {
            // Fail loudly: an unknown command is not an empty success (AGENTS.md section 3).
            throw new AgentException(400, "INVALID_REQUEST", "the proxy has no command \"" + command + "\"",
                    Collections.singletonMap("command", command));
        }
        return String.join("\n", sender.lines());
    }

    @Override
    public List<String> players(long timeoutMs) {
        return thread.call(() -> {
            List<ProxiedPlayer> online = new ArrayList<>(proxy.getPlayers());
            online.sort(BY_NAME);
            List<String> names = new ArrayList<>();
            for (ProxiedPlayer player : online) {
                names.add(player.getName());
            }
            return names;
        }, timeoutMs);
    }

    /** {@code proxy.json#/$defs/player}: a proxy has no inventory or location, only the server the player is on. */
    @Override
    public Map<String, Object> player(String name, long timeoutMs) {
        return thread.call(() -> {
            ProxiedPlayer player = proxy.getPlayer(name);
            if (player == null) {
                throw playerNotFound(name, "no player named " + name);
            }
            Server connection = player.getServer();
            if (connection == null) {
                // On the proxy but not yet connected to a backend: the harness awaits this, so it is "not there yet".
                throw playerNotFound(name, player.getName() + " is on the proxy but not on a server yet");
            }
            Map<String, Object> where = new LinkedHashMap<>();
            where.put("server", connection.getInfo().getName());
            where.put("previousServer", previous.of(player.getUniqueId()));
            Map<String, Object> state = new LinkedHashMap<>();
            state.put("name", player.getName());
            state.put("uuid", player.getUniqueId().toString());
            state.put("proxy", where);
            return state;
        }, timeoutMs);
    }

    private static AgentException playerNotFound(String name, String message) {
        return new AgentException(404, "PLAYER_NOT_FOUND", message, Collections.singletonMap("name", name));
    }

    @Override
    public List<Map<String, Object>> proxyServers(long timeoutMs) {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
        List<PingedServer> pinged = thread.call(() -> {
            List<ServerInfo> servers = new ArrayList<>(proxy.getServers().values());
            servers.sort(Comparator.comparing(ServerInfo::getName));
            List<PingedServer> out = new ArrayList<>();
            for (ServerInfo server : servers) {
                out.add(new PingedServer(server));
            }
            return out;
        }, timeoutMs);
        List<Map<String, Object>> out = new ArrayList<>();
        for (PingedServer server : pinged) {
            out.add(server.describe(remainingMs(deadline)));
        }
        return out;
    }

    @Override
    public Map<String, Object> proxyServer(String name, long timeoutMs) {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
        PingedServer pinged = thread.call(() -> {
            ServerInfo found = proxy.getServerInfo(name);
            if (found == null) {
                throw new AgentException(404, "SERVER_NOT_FOUND", "the proxy has no server named " + name,
                        Collections.singletonMap("name", name));
            }
            return new PingedServer(found);
        }, timeoutMs);
        return pinged.describe(remainingMs(deadline));
    }

    /** A backend and its ping, started on the scheduler and read outside it so a slow backend holds no worker. */
    private static final class PingedServer {

        private final ServerInfo info;
        private final int players;
        private final CompletableFuture<Boolean> online = new CompletableFuture<>();

        PingedServer(ServerInfo server) {
            this.info = server;
            this.players = server.getPlayers().size();
            // The proxy's own ping: a reply is online, an error (refused, unresolved, reset) is not.
            server.ping((result, error) -> online.complete(error == null && result != null));
        }

        Map<String, Object> describe(long timeoutMs) {
            boolean up = ProxyThread.await(online, timeoutMs);
            SocketAddress address = info.getSocketAddress();
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("name", info.getName());
            out.put("address", address instanceof InetSocketAddress
                    ? ((InetSocketAddress) address).getHostString() + ":" + ((InetSocketAddress) address).getPort()
                    : String.valueOf(address));
            out.put("online", up);
            out.put("players", players);
            return out;
        }
    }

    @Override
    public String subscribe(String className, long timeoutMs) {
        return thread.call(() -> probes.subscribe(className), timeoutMs);
    }

    @Override
    public Map<String, Object> events(String probeId, long since, long timeoutMs) {
        return thread.call(() -> probes.events(probeId, since), timeoutMs);
    }

    @Override
    public void unsubscribe(String probeId, long timeoutMs) {
        thread.call(() -> {
            probes.unsubscribe(probeId);
            return null;
        }, timeoutMs);
    }

    @Override
    public Map<String, Object> log(long since, String minLevel, long timeoutMs) {
        return logs.read(since, minLevel);
    }

    private static long remainingMs(long deadlineNanos) {
        // At least 1 ms, so a spent budget still reads the future once and then times out with the standard error.
        return Math.max(1, TimeUnit.NANOSECONDS.toMillis(deadlineNanos - System.nanoTime()));
    }

    // Everything below is game state a proxy does not have (spec §2): a 404, never an empty success.

    @Override
    public void give(String name, Map<String, Object> item, long timeoutMs) {
        throw notServed();
    }

    @Override
    public void reset(String name, long timeoutMs) {
        throw notServed();
    }

    @Override
    public void patch(String name, Map<String, Object> changes, long timeoutMs) {
        throw notServed();
    }

    @Override
    public Map<String, Object> block(String world, int x, int y, int z, long timeoutMs) {
        throw notServed();
    }

    @Override
    public Map<String, Object> setBlock(String world, int x, int y, int z, Map<String, Object> write, long timeoutMs) {
        throw notServed();
    }

    @Override
    public Map<String, Object> ticks(long timeoutMs) {
        throw notServed();
    }

    @Override
    public Map<String, Object> awaitTicks(long n, long timeoutMs) {
        throw notServed();
    }

    @Override
    public Object pluginConfig(String plugin, String path, long timeoutMs) {
        throw notServed();
    }

    @Override
    public void patchPluginConfig(String plugin, String path, Object value, long timeoutMs) {
        throw notServed();
    }

    @Override
    public void restorePluginConfig(String plugin, long timeoutMs) {
        throw notServed();
    }

    @Override
    public void reloadPlugin(String plugin, String hookCommand, long timeoutMs) {
        throw notServed();
    }

    @Override
    public Map<String, Object> snapshot(long timeoutMs) {
        throw notServed();
    }

    private static AgentException notServed() {
        return new AgentException(404, "NOT_FOUND", "a proxy agent does not serve this endpoint (game state lives on the backends)",
                null);
    }
}
