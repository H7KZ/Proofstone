package io.github.h7kz.proofstone.core.host;

import io.github.h7kz.proofstone.core.cell.TopologyCell;
import java.nio.file.Path;
import java.util.List;

/** The planned velocity topology cell (versions.yml), modern forwarding, two paper backends. */
class VelocityTopologyIT extends TopologyContract {

    @Override
    TopologyCell cell(List<String> backends) {
        return Topologies.velocity(backends);
    }

    @Override
    Path proxyAgentJar() {
        return Topologies.velocityAgentJar();
    }
}
