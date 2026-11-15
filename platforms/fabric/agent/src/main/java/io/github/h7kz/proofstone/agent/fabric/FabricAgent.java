package io.github.h7kz.proofstone.agent.fabric;

import io.github.h7kz.proofstone.agent.log.LogBuffer;
import io.github.h7kz.proofstone.agent.protocol.Health;
import io.github.h7kz.proofstone.agent.server.AgentConfig;
import io.github.h7kz.proofstone.agent.server.AgentServer;
import io.github.h7kz.proofstone.agent.vanilla.ServerThread;
import io.github.h7kz.proofstone.agent.vanilla.TickCounter;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The running agent: the game view, probes, log and ticks behind the HTTP server. Split from the mod entrypoint so the
 * contract test can start it on a mock view. Started once the server has started (ADR-0017 section 5), so a reply to
 * {@code /v1/health} means the server is ready.
 */
final class FabricAgent implements AutoCloseable {

    static final String PROTOCOL = "1.3";
    static final String PLATFORM = "fabric";

    private final AgentServer server;

    private FabricAgent(AgentServer server) {
        this.server = server;
    }

    /**
     * The tokens the agent answers for (test-api section 10). {@code mods} is the ADR-0017 section 8 token for
     * {@code GET /v1/mods}, {@code datapacks} is the one of
     * {@code GET /v1/datapacks} (T-M8-09). Not claimed: {@code config} and {@code reload} (a Fabric server has no plugins)
     * and {@code resource-pack-status} (no read-only hook was
     * run, ADR-0017 section 7).
     */
    static List<String> capabilities() {
        return Arrays.asList("command", "players", "world", "events", "log", "ticks", "mods", "datapacks");
    }

    /**
     * Starts the HTTP server. Its threads are daemon threads (ADR-0017 section 5): a JVM whose server failed must end,
     * not wait on the agent. The JDK's HTTP server takes the daemon flag from the thread that starts it.
     */
    static FabricAgent start(AgentConfig config, Executor serverThread, FabricView view, FabricEventProbes probes,
            LogBuffer logs, TickCounter ticks, String agentVersion, String mcVersion) throws IOException {
        Health health = new Health(agentVersion, PROTOCOL, PLATFORM, mcVersion, capabilities());
        FabricGame game = new FabricGame(new ServerThread(serverThread), view, probes, logs, ticks);
        AtomicReference<Object> result = new AtomicReference<>();
        Thread starter = new Thread(() -> {
            try {
                AgentServer server = new AgentServer(config, health, game);
                server.start();
                result.set(server);
            } catch (IOException | RuntimeException e) {
                result.set(e);
            }
        }, "proofstone-agent-start");
        starter.setDaemon(true);
        starter.start();
        try {
            starter.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted while starting the agent", e);
        }
        Object started = result.get();
        if (started instanceof IOException) {
            throw (IOException) started;
        }
        if (started instanceof RuntimeException) {
            throw (RuntimeException) started;
        }
        return new FabricAgent((AgentServer) started);
    }

    int port() {
        return server.port();
    }

    @Override
    public void close() {
        server.stop();
    }
}
