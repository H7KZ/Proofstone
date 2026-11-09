package io.github.h7kz.proofstone.agent.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.proxy.ProxyServer;
import java.io.IOException;
import org.slf4j.Logger;

/**
 * Harness agent entry point for Velocity. Observes only (ADR-0002, ADR-0016 section 10): it answers the harness and
 * registers no command, cancels nothing and changes no proxy state. A missing token logs an error and leaves the HTTP
 * server down. Velocity finds this class through {@code velocity-plugin.json}, so no annotation processor is needed.
 */
public final class ProofstoneAgentPlugin {

    /** Kept in step with {@code velocity-plugin.json}, which a test compares. */
    static final String VERSION = "0.1.0";

    private final ProxyServer proxy;
    private final Logger logger;
    private VelocityAgent agent;

    @Inject
    public ProofstoneAgentPlugin(ProxyServer proxy, Logger logger) {
        this.proxy = proxy;
        this.logger = logger;
    }

    // PostOrder.LAST on every handler, the agent's own lifecycle ones included (observe-only rule).
    @Subscribe(order = PostOrder.LAST)
    public void onProxyInitialize(ProxyInitializeEvent event) {
        try {
            agent = VelocityAgent.start(proxy, this, logger, VERSION, System.getenv());
            logger.info("Proofstone agent listening on port {}", agent.port());
        } catch (IllegalStateException | IOException e) {
            logger.error("Proofstone agent not started: {}", e.getMessage());
        }
    }

    @Subscribe(order = PostOrder.LAST)
    public void onProxyShutdown(ProxyShutdownEvent event) {
        if (agent != null) {
            agent.close();
        }
    }
}
