package io.github.h7kz.proofstone.examples.badtests;

import io.github.h7kz.proofstone.api.Agent;
import io.github.h7kz.proofstone.api.CellTest;
import io.github.h7kz.proofstone.api.Client;
import io.github.h7kz.proofstone.api.DualAssert;
import io.github.h7kz.proofstone.api.Matrix;
import io.github.h7kz.proofstone.api.Platform;
import io.github.h7kz.proofstone.api.Proofstone;

/**
 * A deliberately vacuous test (T-M4-03, self-tests section 2). It names the fixture as its plugin under test, but
 * asserts only what vanilla does when a player joins and is reset: a fresh player has level 0. It passes with the
 * plugin and without it, so a control run must report it as VACUOUS and fail it.
 *
 * <p>It is a bad test on purpose. It stays out of user-facing suites: the meta-test (T-M4-07) asserts its verdict.
 */
@Proofstone(plugins = "build/fixture-plugins/proofstone-fixture.jar")
@Matrix(platform = Platform.PAPER, versions = {"latest"})
class VacuousE2E {

    @CellTest
    void aFreshPlayerHasNoExperienceLevel(Agent agent, Client client) {
        DualAssert.level(agent.player(client), client).isEqualTo(0);
    }
}
