package dev.proofstone.examples.smoke.nested;

import dev.proofstone.api.Agent;
import dev.proofstone.api.CellTest;
import dev.proofstone.api.Client;
import dev.proofstone.api.Matrix;
import dev.proofstone.api.Platform;
import dev.proofstone.api.Proofstone;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.TestMethodOrder;

/**
 * A scenario that must FAIL: its first test logs a server error and asserts nothing about it. It is not run by
 * Gradle (the build excludes this package); {@code ErrorLogFailsTheTestE2E} runs it through the JUnit launcher and
 * checks that the first test fails with the log record and the second, clean one passes.
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
    void nextTestIsCleanBecauseTheLogCursorMovedOn(Agent agent, Client client) {
        agent.command("scoreboard objectives add nested2 dummy");
    }
}
