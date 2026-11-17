package io.github.h7kz.proofstone.agent.bukkit;

import java.util.List;
import java.util.function.Predicate;

/**
 * Adds the {@code floodgate} capability token when the plugin named {@code floodgate} is enabled (ADR-0018 section 5).
 * Read-only and by name: no Floodgate class is touched, so there is no dependency on it. It runs on the first global tick,
 * after every plugin has enabled, because the agent itself enables first ({@code load: STARTUP}); a Floodgate that failed
 * to load never adds the token. Later ticks do nothing.
 */
final class FloodgateProbe implements Runnable {

    static final String PLUGIN = "floodgate";
    static final String TOKEN = "floodgate";

    private final List<String> tokens;
    private final Predicate<String> enabled;
    private boolean done;

    FloodgateProbe(List<String> tokens, Predicate<String> enabled) {
        this.tokens = tokens;
        this.enabled = enabled;
    }

    @Override
    public void run() {
        if (done) {
            return;
        }
        done = true;
        if (enabled.test(PLUGIN) && !tokens.contains(TOKEN)) {
            tokens.add(TOKEN);
        }
    }
}
