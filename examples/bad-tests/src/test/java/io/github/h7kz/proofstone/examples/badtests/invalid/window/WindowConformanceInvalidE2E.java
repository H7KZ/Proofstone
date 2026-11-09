package io.github.h7kz.proofstone.examples.badtests.invalid.window;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.h7kz.proofstone.api.Agent;
import io.github.h7kz.proofstone.api.CellTest;
import io.github.h7kz.proofstone.api.Matrix;
import io.github.h7kz.proofstone.api.Platform;
import io.github.h7kz.proofstone.api.Proofstone;

/**
 * A deliberately invalid test for the window scenario (T-M4-10b, self-tests section 1). Its own assertions hold on every
 * server. With {@code -Dproofstone.injectConformanceFault=window} the per-cell check's chest scenario (C04) expects a
 * chest the vanilla server never opens, so the cell's conformance fails and every test on it must report
 * {@code INVALID}. Both tests below are on the same cell, so both must be {@code INVALID}.
 *
 * <p>Excluded from every suite except the meta-test (the bad-tests build), so it never shows up in a normal run.
 */
@Proofstone
// Bridged (ViaProxy), so the window scenarios C04 to C08 run here. On the native 26.3 row they do not (T-M4-10b).
@Matrix(platform = Platform.PAPER, versions = {"26.1.2"})
class WindowConformanceInvalidE2E {

    @CellTest
    void aWindowBrokenCellGivesNoPluginVerdictForThisTest(Agent agent) {
        assertThat(agent.capabilities()).isNotEmpty();
    }

    @CellTest
    void aSecondTestOnTheSameWindowBrokenCellIsInvalidToo(Agent agent) {
        assertThat(agent.capabilities()).isNotEmpty();
    }
}
