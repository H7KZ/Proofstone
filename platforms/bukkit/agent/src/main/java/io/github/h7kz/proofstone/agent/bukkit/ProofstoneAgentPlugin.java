package io.github.h7kz.proofstone.agent.bukkit;

import io.github.h7kz.proofstone.agent.log.LogBuffer;
import io.github.h7kz.proofstone.agent.platform.MinecraftVersion;
import io.github.h7kz.proofstone.agent.platform.PlatformDetector;
import io.github.h7kz.proofstone.agent.protocol.Health;
import io.github.h7kz.proofstone.agent.scheduler.FoliaScheduler;
import io.github.h7kz.proofstone.agent.scheduler.MainThreadScheduler;
import io.github.h7kz.proofstone.agent.scheduler.Scheduler;
import io.github.h7kz.proofstone.agent.server.AgentConfig;
import io.github.h7kz.proofstone.agent.server.AgentServer;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Harness agent entry point. Observes only (ADR-0002): it answers the harness and changes no game state.
 * A missing token logs SEVERE and leaves the HTTP server down.
 */
public final class ProofstoneAgentPlugin extends JavaPlugin {

    private static final String PROTOCOL = "1.2";

    private LogBuffer logs;
    private Scheduler scheduler;
    private AgentServer server;

    @Override
    public void onEnable() {
        logs = LogBuffer.attach();
        try {
            AgentConfig config = AgentConfig.fromEnvironment(System.getenv());
            String platform = PlatformDetector.detect(this::classPresent);
            // Folia has no main thread, so the Bukkit scheduler refuses every task. It gets the region scheduler.
            scheduler = "folia".equals(platform) ? new FoliaScheduler(this) : new MainThreadScheduler(this);
            TickCounter ticks = TickCounter.start(scheduler);
            Health health = new Health(
                    getDescription().getVersion(),
                    PROTOCOL,
                    platform,
                    MinecraftVersion.parse(Bukkit.getBukkitVersion()),
                    capabilities(platform, Bukkit.getOnlineMode()));
            EventProbes probes = new EventProbes(this, ticks);
            server = new AgentServer(config, health,
                    new BukkitGame(scheduler, probes, logs, ticks, "folia".equals(platform)));
            server.start();
            getLogger().info("Proofstone agent listening on port " + server.port());
        } catch (IllegalStateException | IOException e) {
            getLogger().severe("Proofstone agent not started: " + e.getMessage());
        }
    }

    /**
     * The tokens the agent answers for. {@code regions} is reported on Folia only: it confirms the cell holds
     * FOLIA_REGIONS (docs/platforms/folia.md), and ticks.json carries per-region counters for it. {@code bedrock-prefix}
     * is reported only offline: an online server authenticates the name with Mojang, so a prefixed login cannot succeed
     * there (SIMULATED_BEDROCK, docs/platforms/paper.md). The pack still has to relax Paper's username validation.
     */
    static List<String> capabilities(String platform, boolean onlineMode) {
        List<String> tokens = new ArrayList<>(Arrays.asList(
                "command", "players", "config", "reload", "world", "events", "log", "ticks"));
        if (!onlineMode) {
            tokens.add("bedrock-prefix");
        }
        if ("folia".equals(platform)) {
            tokens.add("regions");
        }
        return tokens;
    }

    @Override
    public void onDisable() {
        if (server != null) {
            server.stop();
        }
        if (logs != null) {
            logs.detach();
        }
        if (scheduler != null) {
            scheduler.close();
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
}
