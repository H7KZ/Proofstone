package io.github.h7kz.proofstone.agent.velocity;

import com.velocitypowered.api.proxy.ProxyServer;
import io.github.h7kz.proofstone.agent.log.LogBuffer;
import io.github.h7kz.proofstone.agent.protocol.Health;
import io.github.h7kz.proofstone.agent.server.AgentConfig;
import io.github.h7kz.proofstone.agent.server.AgentServer;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;

/**
 * The running agent: log capture, the previous-server tracker, the game view and the HTTP server, wired from a
 * {@link ProxyServer}. Split from the plugin class so the contract test can start it on a mock proxy.
 */
public final class VelocityAgent implements AutoCloseable {

    static final String PROTOCOL = "1.2";
    static final String PLATFORM = "velocity";

    private final LogBuffer logs;
    private final AgentServer server;
    private final PreviousServers previous;
    private final VelocityEventProbes probes;

    private VelocityAgent(LogBuffer logs, AgentServer server, PreviousServers previous, VelocityEventProbes probes) {
        this.logs = logs;
        this.server = server;
        this.previous = previous;
        this.probes = probes;
    }

    /**
     * Starts the agent from the environment (spec §1). Throws {@link IllegalStateException} for a missing token and
     * {@link IOException} when the port cannot be bound; a half-started agent is closed first.
     */
    public static VelocityAgent start(ProxyServer proxy, Object plugin, Logger logger, String agentVersion,
            Map<String, String> env) throws IOException {
        AgentConfig config = AgentConfig.fromEnvironment(env);
        LogBuffer logs = LogBuffer.attachPreferringLog4j();
        PreviousServers previous = null;
        VelocityEventProbes probes = null;
        try {
            ProxyThread thread = new ProxyThread(proxy, plugin);
            previous = PreviousServers.track(proxy, plugin);
            probes = new VelocityEventProbes(proxy, plugin);
            // The proxy's version is the "Minecraft version" of a proxy health reply (spec §2).
            Health health = new Health(agentVersion, PROTOCOL, PLATFORM, proxy.getVersion().getVersion(),
                    capabilities());
            AgentServer server = new AgentServer(config, health,
                    new VelocityGame(proxy, thread, probes, logs, previous));
            server.start();
            logger.debug("Proofstone agent HTTP server is up on port {}", server.port());
            return new VelocityAgent(logs, server, previous, probes);
        } catch (IOException | RuntimeException e) {
            if (probes != null) {
                probes.close();
            }
            if (previous != null) {
                previous.close();
            }
            logs.detach();
            throw e;
        }
    }

    /**
     * The tokens the agent answers for. {@code events} is withheld on purpose: the probes are unit-tested on a mock
     * proxy but no real Velocity has run them yet, so the token waits for T-M7-11 (docs/platforms/velocity.md).
     */
    static List<String> capabilities() {
        return Arrays.asList("command", "players", "log", "proxy");
    }

    public int port() {
        return server.port();
    }

    @Override
    public void close() {
        server.stop();
        probes.close();
        previous.close();
        logs.detach();
    }
}
