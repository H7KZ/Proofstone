package io.github.h7kz.proofstone.examples.badtests;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.h7kz.proofstone.api.Agent;
import io.github.h7kz.proofstone.api.CellTest;
import io.github.h7kz.proofstone.api.Client;
import io.github.h7kz.proofstone.api.DualAssert;
import io.github.h7kz.proofstone.api.Matrix;
import io.github.h7kz.proofstone.api.Platform;
import io.github.h7kz.proofstone.api.Proofstone;
import java.util.Random;

/**
 * A deliberately flaky test (T-M4-04, self-tests section 4). Its real-server check always holds, but its outcome is
 * drawn from a seeded generator, so a run passes or fails on the draw. A plain run passes on its first draw, so the
 * default run stays green. With {@code -Dproofstone.repeat=5} the five draws mix pass and fail, and the flake
 * detector must report {@code FLAKY}.
 *
 * <p>The seed is fixed, so the sequence is the same on every run and the acceptance does not depend on luck. It is not
 * a retry: every draw is one run, and no run is repeated to turn a failure green.
 */
@Proofstone(plugins = "build/fixture-plugins/proofstone-fixture.jar")
@Matrix(platform = Platform.PAPER, versions = {"latest"})
class FlakyE2E {

    /** Seed 3 draws pass, pass, fail, pass, fail: the first draw passes, and five repeats mix both outcomes. */
    private static final long SEED = 3L;
    private static final Random OUTCOMES = new Random(SEED);

    @CellTest
    void theOutcomeOfThisRunIsRandom(Agent agent, Client client) {
        // The real-server part: a fresh player on a real server has no experience level, every run.
        DualAssert.level(agent.player(client), client).isEqualTo(0);
        // The flaky part: the verdict of this run depends on the draw, not on the server.
        assertThat(OUTCOMES.nextBoolean()).as("this run's random draw").isTrue();
    }
}
