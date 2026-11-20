package io.github.h7kz.proofstone.conformance;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.h7kz.proofstone.api.Agent;
import io.github.h7kz.proofstone.api.CellTest;
import io.github.h7kz.proofstone.api.Client;
import io.github.h7kz.proofstone.api.Matrix;
import io.github.h7kz.proofstone.api.Platform;
import io.github.h7kz.proofstone.api.Proofstone;
import java.util.List;

/**
 * C-L02 (specs/self-tests.md section 1, T-M11-03): {@code Agent.bootLogLines} returns what the server logged from the
 * agent's enable up to the test start: the fixture plugin's enable record (it enables after the STARTUP agent) and the
 * {@code Done ... For help, type} line, and none of the test's own lines, which {@code logLines} holds.
 */
@Proofstone
@Matrix(platform = Platform.PAPER, versions = {"latest"})
class ConformanceBootLogLinesTest {

    @CellTest
    void bootLogLinesHoldThePluginEnableLineAndNotTheTestsOwnLines(Agent agent, Client client) {
        List<String> boot = agent.bootLogLines();

        assertThat(boot).as("the fixture plugin's enable record").anyMatch(
                line -> line.startsWith("WARN ") && line.endsWith(": fixture warning for the log contract"));
        assertThat(boot).as("the start-up line").anyMatch(line -> line.contains("For help, type"));
        assertThat(boot).as("no line of the test window").noneMatch(line -> line.contains(client.name() + " joined"));

        List<String> since = agent.logLines();
        assertThat(since).as("lines since the test start are disjoint from the boot").noneMatch(boot::contains);
    }
}
