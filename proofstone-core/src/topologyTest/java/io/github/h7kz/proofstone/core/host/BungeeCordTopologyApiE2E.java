package io.github.h7kz.proofstone.core.host;

import io.github.h7kz.proofstone.api.Matrix;
import io.github.h7kz.proofstone.api.Platform;
import io.github.h7kz.proofstone.api.Proofstone;
import io.github.h7kz.proofstone.api.Topology;

/** The public topology API on the BungeeCord pairing of versions.yml (T-M7-10); the tests are in TopologyApiContract. */
@Proofstone
@Matrix(platform = Platform.BUNGEECORD, versions = {"latest"})
@Topology.Backends({"lobby", "survival"})
class BungeeCordTopologyApiE2E extends TopologyApiContract {
}
