package io.github.h7kz.proofstone.agent.bungee;

import java.io.IOException;
import net.md_5.bungee.api.plugin.Plugin;

/**
 * Harness agent entry point for BungeeCord. Observes only (ADR-0002, ADR-0016 section 10): it answers the harness and
 * registers no command, cancels nothing and changes no proxy state. A missing token logs an error and leaves the HTTP
 * server down. BungeeCord finds this class through {@code bungee.yml}.
 */
public final class ProofstoneAgentPlugin extends Plugin {

    /** Kept in step with {@code bungee.yml}, which a test compares. */
    static final String VERSION = "0.1.0";

    private BungeeAgent agent;

    @Override
    public void onEnable() {
        try {
            agent = BungeeAgent.start(this, VERSION, System.getenv());
            getLogger().info("Proofstone agent listening on port " + agent.port());
        } catch (IllegalStateException | IOException e) {
            getLogger().severe("Proofstone agent not started: " + e.getMessage());
        }
    }

    @Override
    public void onDisable() {
        if (agent != null) {
            agent.close();
        }
    }
}
