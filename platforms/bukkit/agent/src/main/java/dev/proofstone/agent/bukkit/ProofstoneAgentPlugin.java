package dev.proofstone.agent.bukkit;

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

    private static final String PROTOCOL = "1.0";

    private AgentServer server;

    @Override
    public void onEnable() {
        try {
            AgentConfig config = AgentConfig.fromEnvironment(System.getenv());
            Health health = new Health(
                    getDescription().getVersion(),
                    PROTOCOL,
                    PlatformDetector.detect(this::classPresent),
                    minecraftVersion(),
                    Arrays.asList("command", "players"));
            server = new AgentServer(config, health, new BukkitGame(new MainThreadScheduler(this)));
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
    }

    private boolean classPresent(String name) {
        try {
            Class.forName(name, false, getClass().getClassLoader());
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /** {@code 1.8.8-R0.1-SNAPSHOT} becomes {@code 1.8.8}. An empty result is an error, never a guess. */
    private static String minecraftVersion() {
        String version = Bukkit.getBukkitVersion().split("-")[0];
        if (version.isEmpty()) {
            throw new IllegalStateException("cannot read the Minecraft version from Bukkit");
        }
        return version;
    }
}
