package io.github.h7kz.proofstone.examples.badtests.requires;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.h7kz.proofstone.api.Agent;
import io.github.h7kz.proofstone.api.Capability;
import io.github.h7kz.proofstone.api.CellTest;
import io.github.h7kz.proofstone.api.Matrix;
import io.github.h7kz.proofstone.api.Platform;
import io.github.h7kz.proofstone.api.Proofstone;
import io.github.h7kz.proofstone.api.Requires;

/**
 * Capability requirements on a real Paper cell (T-M5-01, test-api spec section 10).
 *
 * <p>{@code skippedWithoutFolia} needs {@code FOLIA_REGIONS}, which no Paper cell holds. Its body fails if it runs, so the
 * test must be SKIPPED with {@code missing capability FOLIA_REGIONS on cell}, and metaRequires checks that reason in the
 * report. {@code runsWithHeldCapabilities} needs what Paper holds, so it passes: a held requirement never skips.
 *
 * <p>It is excluded from every suite except the meta-test (the bad-tests build), so it never shows up in a normal run.
 */
@Proofstone
@Matrix(platform = Platform.PAPER, versions = {"latest"})
class RequiresSkipsE2E {

    @CellTest
    @Requires(Capability.FOLIA_REGIONS)
    void skippedWithoutFolia() {
        throw new AssertionError("the body ran on a cell without FOLIA_REGIONS: the skip did not happen");
    }

    @CellTest
    @Requires({Capability.BUKKIT_EVENTS, Capability.CONSOLE})
    void runsWithHeldCapabilities(Agent agent) {
        assertThat(agent.capabilities()).contains("events", "command");
    }
}
