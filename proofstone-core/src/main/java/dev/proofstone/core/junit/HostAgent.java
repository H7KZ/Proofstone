package dev.proofstone.core.junit;

import dev.proofstone.api.Agent;
import dev.proofstone.core.host.PaperHost;
import java.util.List;

/** The thin {@link Agent} over the host's agent client. */
record HostAgent(PaperHost host) implements Agent {

    @Override
    public String command(String command) {
        return host.command(command);
    }

    @Override
    public List<String> capabilities() {
        return host.health().capabilities();
    }
}
