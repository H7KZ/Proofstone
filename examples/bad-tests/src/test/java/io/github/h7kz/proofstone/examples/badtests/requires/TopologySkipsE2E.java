package io.github.h7kz.proofstone.examples.badtests.requires;

import io.github.h7kz.proofstone.api.CellTest;
import io.github.h7kz.proofstone.api.Matrix;
import io.github.h7kz.proofstone.api.Platform;
import io.github.h7kz.proofstone.api.Proofstone;
import io.github.h7kz.proofstone.api.Topology;

/**
 * A topology test planned on a single-server Paper cell (T-M7-10, ADR-0016 section 10). {@code @Topology.Backends} makes
 * it need {@code TOPOLOGY}, which only a proxy pack declares, so the test is SKIPPED with
 * {@code missing capability TOPOLOGY on cell} before any server boots, and its body (which fails if it runs) never runs.
 * metaRequires checks that reason in the report. Excluded from every suite except the meta-test.
 */
@Proofstone
@Matrix(platform = Platform.PAPER, versions = {"latest"})
@Topology.Backends
class TopologySkipsE2E {

    @CellTest
    void skippedOnASingleServerCell(Topology topology) {
        throw new AssertionError("the body ran on a single-server cell: the TOPOLOGY skip did not happen");
    }
}
