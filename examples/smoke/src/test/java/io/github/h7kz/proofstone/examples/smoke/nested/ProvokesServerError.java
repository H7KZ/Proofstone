package io.github.h7kz.proofstone.examples.smoke.nested;

import io.github.h7kz.proofstone.api.Agent;
import io.github.h7kz.proofstone.api.CellTest;
import io.github.h7kz.proofstone.api.Client;
import io.github.h7kz.proofstone.api.Matrix;
import io.github.h7kz.proofstone.api.Platform;
import io.github.h7kz.proofstone.api.Proofstone;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.TestMethodOrder;

/**
 * A scenario that must FAIL: its first two tests log a server error (through JUL and through Log4j) and assert
 * nothing about it. It is not run by Gradle (the build excludes this package); {@code ErrorLogFailsTheTestE2E} runs
 * it through the JUnit launcher and checks that each of them fails with its log record and the third, clean one
 * passes.
 */
@Proofstone(plugins = "build/fixture-plugins/proofstone-fixture.jar")
@Matrix(platform = Platform.PAPER, versions = {"latest"})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ProvokesServerError {

    public static final String MARKER = "marker-7f3a";

    @CellTest
    @Order(1)
    void logsAnErrorAndChecksNothingItself(Agent agent, Client client) {
        agent.command("fixture-error " + MARKER);
    }

    @CellTest
    @Order(2)
    void logsAnErrorThroughLog4jAndChecksNothingItself(Agent agent, Client client) {
        agent.command("fixture-log4j-error " + MARKER);
    }

    @CellTest
    @Order(3)
    void nextTestIsCleanBecauseTheLogCursorMovedOn(Agent agent, Client client) {
        agent.command("scoreboard objectives add nested2 dummy");
    }
}
