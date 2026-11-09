package io.github.h7kz.proofstone.examples.badtests.expect;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.h7kz.proofstone.api.Agent;
import io.github.h7kz.proofstone.api.Cell;
import io.github.h7kz.proofstone.api.CellExpectation;
import io.github.h7kz.proofstone.api.CellTest;
import io.github.h7kz.proofstone.api.Expect;
import io.github.h7kz.proofstone.api.Matrix;
import io.github.h7kz.proofstone.api.Platform;
import io.github.h7kz.proofstone.api.Proofstone;

/**
 * A deliberately wrong per-cell expectation (T-M4-15, robustness R7). The test body passes on every server, and so does
 * its log check. The expectation claims the server reports {@code FOLIA_REGIONS}, which a Paper cell never does (a wrong
 * version mapping in miniature), so the test must report {@code FAIL} with the expectation's message.
 *
 * <p>The second test holds its expectation, so it passes: an expectation that holds does not change a pass.
 *
 * <p>It is excluded from every suite except the meta-test (the bad-tests build), so it never shows up in a normal run.
 */
@Proofstone
@Matrix(platform = Platform.PAPER, versions = {"latest"})
class ExpectFailsE2E {

    /** Claims a capability that no Paper cell reports. */
    public static final class FoliaRegionsOnly implements CellExpectation {
        @Override
        public void check(Cell cell, Agent agent) {
            assertThat(agent.capabilities()).as("capabilities on %s", cell.platform() + "@" + cell.version())
                    .contains("FOLIA_REGIONS");
        }
    }

    /** Claims a capability that every Paper cell reports (the agent's own names, docs/platforms/paper.md). */
    public static final class PlayersHold implements CellExpectation {
        @Override
        public void check(Cell cell, Agent agent) {
            assertThat(agent.capabilities()).contains("players");
        }
    }

    @CellTest
    @Expect(FoliaRegionsOnly.class)
    void theWrongExpectationFailsTheTest(Agent agent) {
        assertThat(agent.capabilities()).isNotEmpty();
    }

    @CellTest
    @Expect(PlayersHold.class)
    void aHoldingExpectationLeavesThePass(Agent agent) {
        assertThat(agent.capabilities()).isNotEmpty();
    }
}
