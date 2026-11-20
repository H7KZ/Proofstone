package io.github.h7kz.proofstone.core.junit;

import io.github.h7kz.proofstone.api.Agent;
import io.github.h7kz.proofstone.api.Events;
import io.github.h7kz.proofstone.api.PlayerHandle;
import io.github.h7kz.proofstone.api.PluginHandle;
import io.github.h7kz.proofstone.core.host.AgentEndpoint;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.function.LongSupplier;
import java.util.regex.Pattern;

/**
 * The thin {@link Agent} over the host's agent client.
 *
 * @param patched   the plugins whose config the current test changed, for the lifecycle to restore
 * @param logCursor the server log cursor taken when the test started: {@link #logLines()} reads from it
 */
record HostAgent(AgentEndpoint host, Set<String> patched, LongSupplier logCursor) implements Agent {

    @Override
    public String command(String command) {
        return host.command(command);
    }

    @Override
    public PlayerHandle player(String name) {
        return new HostPlayer(host, name);
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
