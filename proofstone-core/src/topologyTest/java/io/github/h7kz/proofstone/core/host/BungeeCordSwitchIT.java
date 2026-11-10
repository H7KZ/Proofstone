package io.github.h7kz.proofstone.core.host;

import io.github.h7kz.proofstone.core.cell.TopologyCell;
import java.nio.file.Path;
import java.util.List;

/** The server switch on the planned bungeecord topology cell (versions.yml), legacy forwarding. */
class BungeeCordSwitchIT extends SwitchContract {

    @Override
    TopologyCell cell(List<String> backends) {
        return Topologies.bungeeCord(backends);
    }

    @Override
    Path proxyAgentJar() {
        return Topologies.bungeeAgentJar();
    }
}
