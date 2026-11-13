package io.github.h7kz.proofstone.examples.badtests.topology;

import io.github.h7kz.proofstone.api.CellTest;
import io.github.h7kz.proofstone.api.Matrix;
import io.github.h7kz.proofstone.api.Platform;
import io.github.h7kz.proofstone.api.Proofstone;
import io.github.h7kz.proofstone.api.Topology;

/**
 * A topology test whose body would pass, on a topology that is broken on purpose (T-M7-13,
 * {@code -Dproofstone.injectTopologyFault}). The topology conformance (T00, P01 to P05) must come back red, so the test is
 * {@code INVALID} and its body never runs. The body fails if it does run: a fault that passed conformance would show here
 * as a plain failure, and the meta run reads it as a fault that was not caught. {@code metaTopology*} checks the message
 * of each fault for the container and the hop (examples/bad-tests/build.gradle.kts). Excluded from every suite except
 * the meta run.
 */
@Proofstone
@Matrix(platform = Platform.VELOCITY, versions = {"latest"})
@Matrix(platform = Platform.BUNGEECORD, versions = {"latest"})
@Topology.Backends({"lobby", "survival"})
class TopologyFaultE2E {

    @CellTest
    void aTestOnABrokenTopologyNeverRuns(Topology topology) {
        throw new AssertionError("the body ran: conformance passed on a topology with an injected fault, so the fault was "
                + "not caught");
    }
}
