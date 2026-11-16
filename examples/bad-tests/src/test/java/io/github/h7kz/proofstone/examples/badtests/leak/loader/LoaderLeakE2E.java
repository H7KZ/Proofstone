package io.github.h7kz.proofstone.examples.badtests.leak.loader;

import io.github.h7kz.proofstone.api.Agent;
import io.github.h7kz.proofstone.api.CellTest;
import io.github.h7kz.proofstone.api.Matrix;
import io.github.h7kz.proofstone.api.Platform;
import io.github.h7kz.proofstone.api.Proofstone;

/**
 * A deliberately leaking test on a loader cell (T-M8-18, robustness R6). It changes the world's {@code random_tick_speed} rule
 * to 7 and never changes it back, through the Fabric agent's console. 7 is not the rule's default (3), which is what a loader
 * cell holds before the test: unlike {@code PaperHost}, {@code LoaderHost} does not set the deterministic-world rules, so a
 * leak test that set 3 would change nothing and pass (seen in the first run of this card). Its own work passes, so the leak detector
 * is the only thing that can fail it: the rule reads back different after the test, and the failure must start with {@code LEAK}
 * and name the rule. The detector snapshots any host, so this shows it works on a loader cell as on Paper
 * ({@code leak/LeakE2E}).
 *
 * <p>Pinned to {@code fabric@26.3} (examples/bad-tests/README.md), where the rule name is the snake_case one. Excluded from every
 * suite except {@code metaModTest} and {@code metaModFabricTest}.
 */
@Proofstone
@Matrix(platform = Platform.FABRIC, versions = {"26.3"})
class LoaderLeakE2E {

    @CellTest
    void leavesTheRandomTickSpeedChangedBehind(Agent agent) {
        agent.command("gamerule random_tick_speed 7");
    }
}
