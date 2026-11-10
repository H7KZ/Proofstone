package io.github.h7kz.proofstone.core.host;

import io.github.h7kz.proofstone.core.cell.TopologyCell;
import java.nio.file.Path;
import java.util.List;

/** BungeeCord build 2111, legacy forwarding, two paper@26.3 backends. */
class BungeeCordTopologyIT extends TopologyContract {

    @Override
    TopologyCell cell(List<String> backends) {
        return Topologies.bungeeCord(backends);
    }

    @Override
    Path proxyAgentJar() {
        return Topologies.bungeeAgentJar();
    }
}
