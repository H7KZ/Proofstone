package io.github.h7kz.proofstone.core.junit;

import io.github.h7kz.proofstone.api.Agent;
import io.github.h7kz.proofstone.api.Topology;
import io.github.h7kz.proofstone.core.host.TopologyHost;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The API view of a running topology for one test. {@code patched} holds, per container ({@code proxy} or a backend
 * name), the plugins whose config the test changed, so the lifecycle restores each on its own agent.
 */
record TopologyView(TopologyHost host, Map<String, Set<String>> patched) implements Topology {

    @Override
    public Agent server(String name) {
        // backend() names the backends there are when the name is unknown.
        return new HostAgent(host.backend(name), patched.get(name));
    }

    @Override
    public Agent proxy() {
        return new ProxyAgent(host.proxy(), patched.get(TopologyHost.PROXY_ALIAS));
    }

    @Override
    public List<String> servers() {
        return host.backendNames();
    }
}
