package io.github.h7kz.proofstone.core.junit;

import io.github.h7kz.proofstone.api.Agent;
import io.github.h7kz.proofstone.api.Events;
import io.github.h7kz.proofstone.api.PlayerHandle;
import io.github.h7kz.proofstone.api.PluginHandle;
import io.github.h7kz.proofstone.core.host.ProxyHost;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.function.LongSupplier;
import java.util.regex.Pattern;

/**
 * The proxy's {@link Agent} ({@code Topology.proxy()}). A proxy serves no game state (agent-protocol section 2), so
 * {@link #player} fails loudly instead of returning an empty state; a player is read through a backend's agent.
 *
 * @param patched   the plugins whose config the current test changed, for the lifecycle to restore
 * @param logCursor the proxy log cursor taken when the test started: {@link #logLines()} reads from it
 */
record ProxyAgent(ProxyHost host, Set<String> patched, LongSupplier logCursor) implements Agent {

    @Override
    public String command(String command) {
        return host.command(command);
    }

    @Override
    public PlayerHandle player(String name) {
        throw new UnsupportedOperationException("the " + host.kind().id() + " proxy agent holds no game state for "
                + name + ": read the player through topology.server(<backend>).player(...) (ADR-0016 section 6)");
    }

    @Override
    public PluginHandle plugin(String name) {
        return new HostPlugin(host, name, patched);
    }

    @Override
    public Events events() {
        return new HostEvents(host);
    }

    @Override
    public List<String> capabilities() {
        return host.health().capabilities();
    }

    @Override
    public List<String> logLines() {
        return ServerLogLines.read(host, logCursor.getAsLong());
    }

    @Override
    public List<String> bootLogLines() {
        return ServerLogLines.readBoot(host, logCursor.getAsLong());
    }

    @Override
    public List<String> logLines(Pattern pattern, Duration timeout) {
        return ServerLogLines.await(host, logCursor, pattern, timeout);
    }
}
