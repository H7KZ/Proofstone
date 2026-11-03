package dev.proofstone.agent.bukkit;

import dev.proofstone.agent.platform.MinecraftVersion;
import dev.proofstone.agent.platform.PlatformDetector;
import dev.proofstone.agent.protocol.Health;
import dev.proofstone.agent.scheduler.MainThreadScheduler;
import dev.proofstone.agent.server.AgentConfig;
import dev.proofstone.agent.server.AgentServer;
import java.io.IOException;
import java.util.Arrays;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Harness agent entry point. Observes only (ADR-0002): it answers the harness and changes no game state.
 * A missing token logs SEVERE and leaves the HTTP server down.
 */
public final class ProofstoneAgentPlugin extends JavaPlugin {

    private static final String PROTOCOL = "1.1";

    private AgentServer server;
    private LogBuffer logs;
    private TickCounter ticks;

    @Override
    public void onEnable() {
        ticks = TickCounter.start(this);
        logs = LogBuffer.attach();
        try {
            AgentConfig config = AgentConfig.fromEnvironment(System.getenv());
            Health health = new Health(
                    getDescription().getVersion(),
                    PROTOCOL,
                    PlatformDetector.detect(this::classPresent),
                    MinecraftVersion.parse(Bukkit.getBukkitVersion()),
                    Arrays.asList("command", "players", "world", "events", "log", "ticks"));
            EventProbes probes = new EventProbes(this, ticks);
            server = new AgentServer(config, health,
                    new BukkitGame(new MainThreadScheduler(this), probes, logs, ticks));
            server.start();
            getLogger().info("Proofstone agent listening on port " + server.port());
        } catch (IllegalStateException | IOException e) {
            getLogger().severe("Proofstone agent not started: " + e.getMessage());
        }
    }

    @Override
    public void onDisable() {
        if (server != null) {
            server.stop();
        }
        if (logs != null) {
            logs.detach();
        }
        Bukkit.getScheduler().cancelTasks(this);
    }

    private boolean classPresent(String name) {
        try {
            Class.forName(name, false, getClass().getClassLoader());
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
