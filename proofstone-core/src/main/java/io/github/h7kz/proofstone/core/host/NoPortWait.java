package io.github.h7kz.proofstone.core.host;

import org.testcontainers.containers.wait.strategy.AbstractWaitStrategy;

/**
 * Testcontainers' wait for a container that has started: nothing. The default waits on the exposed port with a 60 s
 * {@code startupTimeout}, a second clock beside the harness's own boot budgets ({@code bootTimeout}, {@code agentTimeout},
 * the ViaProxy listen wait). A busy host boots Paper in over 60 s and the default kills it as {@code Container startup
 * failed}, with no log tail (T-M8-24). Every host awaits its own readiness and fails fast when the container is not
 * running, so those are the only clocks (T-M8-25).
 */
final class NoPortWait extends AbstractWaitStrategy {

    @Override
    protected void waitUntilReady() {
        // Nothing: the host's own awaits wait, with their documented budget.
    }
}
