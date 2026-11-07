package dev.proofstone.examples.smoke;

import static org.assertj.core.api.Assertions.assertThat;

import dev.proofstone.api.Agent;
import dev.proofstone.api.CellTest;
import dev.proofstone.api.Client;
import dev.proofstone.api.Matrix;
import dev.proofstone.api.Platform;
import dev.proofstone.api.Proofstone;

/**
 * T-M4-02: a failure that leaves a complete repro bundle. The real round trip passes on every run, so the suite
 * stays green. With {@code -Dproofstone.forceFail=true} the test also fails on purpose, and afterEach writes
 * {@code build/proofstone/paper@<mc>/ForcedFailureE2E.failsOnPurposeWhenForced/}.
 */
@Proofstone
@Matrix(platform = Platform.PAPER, versions = {"latest"})
class ForcedFailureE2E {

    @CellTest
    void failsOnPurposeWhenForced(Agent agent, Client client) {
        assertThat(agent.player(client).state().inventory()).isEmpty();
        assertThat(Boolean.getBoolean("proofstone.forceFail")).as("forced failure: -Dproofstone.forceFail=true")
                .isFalse();
    }
}
