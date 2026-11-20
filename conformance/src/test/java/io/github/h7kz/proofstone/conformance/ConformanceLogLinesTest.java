package io.github.h7kz.proofstone.conformance;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.h7kz.proofstone.api.Agent;
import io.github.h7kz.proofstone.api.CellTest;
import io.github.h7kz.proofstone.api.Client;
import io.github.h7kz.proofstone.api.Matrix;
import io.github.h7kz.proofstone.api.Platform;
import io.github.h7kz.proofstone.api.Proofstone;
import java.time.Duration;
import java.util.List;
import java.util.regex.Pattern;

/**
 * C-L01 (specs/self-tests.md section 1, T-M11-01): {@code Agent.logLines} returns the server log since the test started, as
 * plain text read from the server's logger. The harness client's join is logged by the server after the window opens, a
 * console {@code say} adds a line of this test's own, and the boot line the server logged before the window must be absent.
 */
@Proofstone
@Matrix(platform = Platform.PAPER, versions = {"latest"})
@Matrix(platform = Platform.FOLIA, versions = {"latest"})
@Matrix(platform = Platform.PURPUR, versions = {"latest"})
@Matrix(platform = Platform.SPIGOT, versions = {"latest"})
class ConformanceLogLinesTest {

    private static final Duration WAIT = Duration.ofSeconds(10);
    private static final String MARKER = "proofstone-c-l01-marker";

    @CellTest
    void logLinesHoldTheJoinAndTheTestsOwnLineAndNoBootLine(Agent agent, Client client) {
        List<String> join = agent.logLines(Pattern.compile(Pattern.quote(client.name()) + " joined the game"), WAIT);
        assertThat(join).as("the join line of the harness client").hasSize(1);
        assertThat(join.get(0)).startsWith("INFO ");

        agent.command("say " + MARKER);
        List<String> marked = agent.logLines(Pattern.compile(MARKER), WAIT);
        assertThat(marked).as("the console say line").hasSize(1);

        List<String> all = agent.logLines();
        System.err.println("PROOFSTONE C-L01 observed " + all.size() + " lines since the test start; first: "
                + all.get(0) + "; join: " + join.get(0) + "; say: " + marked.get(0));
        assertThat(all).containsAll(join).containsAll(marked);
        // The start-up line is logged before the test's window opens, so it is not part of "since the test started".
        assertThat(all).as("boot lines are before the test window").noneMatch(line -> line.contains("For help, type"));
    }
}
