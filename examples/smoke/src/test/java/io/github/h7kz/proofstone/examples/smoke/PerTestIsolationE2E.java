package io.github.h7kz.proofstone.examples.smoke;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.h7kz.proofstone.api.Agent;
import io.github.h7kz.proofstone.api.CellTest;
import io.github.h7kz.proofstone.api.Isolation;
import io.github.h7kz.proofstone.api.Matrix;
import io.github.h7kz.proofstone.api.Platform;
import io.github.h7kz.proofstone.api.Proofstone;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.TestMethodOrder;

/** T-M2-08: with PER_TEST every test boots its own server, so server state of test one cannot reach test two. */
@Proofstone
@Matrix(platform = Platform.PAPER, versions = {"latest"})
@Isolation(Isolation.Mode.PER_TEST)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PerTestIsolationE2E {

    @CellTest
    @Order(1)
    void firstServerHoldsItsObjective(Agent agent) {
        agent.command("scoreboard objectives add perTestOne dummy");

        assertThat(agent.command("scoreboard objectives list")).contains("perTestOne");
    }

    @CellTest
    @Order(2)
    void secondServerDoesNotHaveIt(Agent agent) {
        // The positive check shows the listing works, so the absence below cannot be an empty reply.
        agent.command("scoreboard objectives add perTestTwo dummy");

        assertThat(agent.command("scoreboard objectives list")).contains("perTestTwo").doesNotContain("perTestOne");
    }
}
