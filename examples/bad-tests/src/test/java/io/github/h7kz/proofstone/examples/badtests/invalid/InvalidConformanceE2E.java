package io.github.h7kz.proofstone.examples.badtests.invalid;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.h7kz.proofstone.api.Agent;
import io.github.h7kz.proofstone.api.CellTest;
import io.github.h7kz.proofstone.api.Matrix;
import io.github.h7kz.proofstone.api.Platform;
import io.github.h7kz.proofstone.api.Proofstone;

/**
 * A deliberately invalid test (T-M4-07, self-tests section 1). Its own assertion holds on every server. The cell's
 * conformance run is the part that can fail: with {@code -Dproofstone.injectConformanceFault=true} the cell's conformance
 * expects text the vanilla server never prints, so the cell fails and this test must report {@code INVALID}. Its body
 * never runs on a failed cell, so the assertion below only matters on a cell whose conformance passes.
 *
 * <p>It is excluded from every suite except the meta-test (the bad-tests build), so it never shows up in a normal run.
 */
@Proofstone
@Matrix(platform = Platform.PAPER, versions = {"latest"})
class InvalidConformanceE2E {

    @CellTest
    void aCellWhoseConformanceFailsGivesNoPluginVerdict(Agent agent) {
        assertThat(agent.capabilities()).isNotEmpty();
    }
}
