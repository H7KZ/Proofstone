package io.github.h7kz.proofstone.agent.bungee;

import io.github.h7kz.proofstone.agent.log.LogBuffer;
import io.github.h7kz.proofstone.agent.protocol.Health;
import io.github.h7kz.proofstone.agent.server.AgentConfig;
import io.github.h7kz.proofstone.agent.server.AgentServer;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.plugin.Plugin;

/**
 * The running agent: log capture, the previous-server tracker, the game view and the HTTP server, wired from a
 * {@link Plugin} on a {@link ProxyServer}. Split from the plugin class so the contract test can start it on a mock proxy.
 */
public final class BungeeAgent implements AutoCloseable {

    static final String PROTOCOL = "1.2";
    static final String PLATFORM = "bungeecord";

    private final Plugin plugin;
    private final LogBuffer logs;
    private final AgentServer server;
    private final PreviousServers previous;
    private final EventProbes probes;

    private BungeeAgent(Plugin plugin, LogBuffer logs, AgentServer server, PreviousServers previous, EventProbes probes) {
        this.plugin = plugin;
        this.logs = logs;
        this.server = server;
        this.previous = previous;
        this.probes = probes;
    }

    /**
     * Starts the agent from the environment (spec §1). Throws {@link IllegalStateException} for a missing token and
     * {@link IOException} when the port cannot be bound; a half-started agent is closed first.
     */
    public static BungeeAgent start(Plugin plugin, String agentVersion, Map<String, String> env) throws IOException {
        ProxyServer proxy = plugin.getProxy();
        AgentConfig config = AgentConfig.fromEnvironment(env);
        // BungeeCord's logger has no parent, so the JUL root logger never sees its records or its plugins' (LogBuffer.attachTo).
        LogBuffer logs = LogBuffer.attachTo(proxy.getLogger());
        PreviousServers previous = null;
        EventProbes probes = null;
        try {
            ProxyThread thread = new ProxyThread(plugin);
            previous = PreviousServers.track(plugin);
            probes = EventProbes.install(plugin);
            // The proxy's version is the "Minecraft version" of a proxy health reply (spec §2).
            Health health = new Health(agentVersion, PROTOCOL, PLATFORM, proxy.getVersion(), capabilities());
            AgentServer server = new AgentServer(config, health, new BungeeGame(proxy, thread, probes, logs, previous));
            server.start();
            plugin.getLogger().fine("Proofstone agent HTTP server is up on port " + server.port());
            return new BungeeAgent(plugin, logs, server, previous, probes);
        } catch (IOException | RuntimeException e) {
            if (probes != null) {
                probes.close(plugin);
            }
            if (previous != null) {
                previous.close(plugin);
            }
            logs.detach();
            throw e;
        }
    }

    /**
     * The tokens the agent answers for. {@code events} is claimed on evidence from a real BungeeCord: the topology conformance P01
     * (T-M7-11) subscribes a probe to the proxy's server-connected event, a client joins through the proxy, and the probe
     * must read that join back (docs/platforms/bungeecord.md).
     */
    static List<String> capabilities() {
        return Arrays.asList("command", "players", "events", "log", "proxy");
    }

    public int port() {
        return server.port();
    }

    @Override
    public void close() {
        server.stop();
        probes.close(plugin);
        previous.close(plugin);
        logs.detach();
    }
}
