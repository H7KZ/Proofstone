package io.github.h7kz.proofstone.core.junit;

import io.github.h7kz.proofstone.api.Agent;
import io.github.h7kz.proofstone.api.Events;
import io.github.h7kz.proofstone.api.PlayerHandle;
import io.github.h7kz.proofstone.api.PluginHandle;
import io.github.h7kz.proofstone.core.host.AgentEndpoint;
import java.util.List;
import java.util.Set;

/**
 * The thin {@link Agent} over the host's agent client.
 *
 * @param patched the plugins whose config the current test changed, for the lifecycle to restore
 */
record HostAgent(AgentEndpoint host, Set<String> patched) implements Agent {

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
}
