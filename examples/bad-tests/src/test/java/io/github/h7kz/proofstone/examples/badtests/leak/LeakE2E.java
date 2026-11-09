package io.github.h7kz.proofstone.examples.badtests.leak;

import io.github.h7kz.proofstone.api.Agent;
import io.github.h7kz.proofstone.api.CellTest;
import io.github.h7kz.proofstone.api.Matrix;
import io.github.h7kz.proofstone.api.Platform;
import io.github.h7kz.proofstone.api.Proofstone;

/**
 * A deliberately leaking test (T-M4-07, robustness R6). It changes the world's {@code random_tick_speed} rule from the
 * harness's 0 and never changes it back. Its own work passes, so the leak detector is the only thing that can fail it:
 * the rule reads back different after the test, and the test must report a {@code LEAK} failure.
 *
 * <p>The rule name is the snake_case one of the pinned 26.1.2 row (docs/platforms/paper.md, gamerule renames). It is
 * excluded from every suite except the meta-test, so it never shows up in a normal run.
 */
@Proofstone
@Matrix(platform = Platform.PAPER, versions = {"26.1.2"})
class LeakE2E {

    @CellTest
    void leavesTheRandomTickSpeedChangedBehind(Agent agent) {
        agent.command("gamerule random_tick_speed 3");
    }
}
