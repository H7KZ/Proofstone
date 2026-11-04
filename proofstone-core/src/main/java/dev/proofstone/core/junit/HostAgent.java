package dev.proofstone.core.junit;

import dev.proofstone.api.Agent;
import dev.proofstone.api.PlayerHandle;
import dev.proofstone.api.PluginHandle;
import dev.proofstone.core.host.PaperHost;
import java.util.List;
import java.util.Set;

/**
 * The thin {@link Agent} over the host's agent client.
 *
 * @param patched the plugins whose config the current test changed, for the lifecycle to restore
 */
record HostAgent(PaperHost host, Set<String> patched) implements Agent {

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
    public List<String> capabilities() {
        return host.health().capabilities();
    }
}
