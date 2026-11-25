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
 * agent's enable up to the test start: the agent plugin's own enable line (logged after its handler attached) and the
 * {@code Done ... For help, type} line, and none of the test's own lines, which {@code logLines} holds. It also pins what is
 * not there: the banner, library loading and the agent's own Enabling line (T-M12-06).
 */
@Proofstone
@Matrix(platform = Platform.PAPER, versions = {"latest"})
class ConformanceBootLogLinesTest {

    @CellTest
    void bootLogLinesHoldThePluginEnableLineAndNotTheTestsOwnLines(Agent agent, Client client) {
        List<String> boot = agent.bootLogLines();

        // The line ProofstoneAgentPlugin.java:55 logs at the end of onEnable, after LogBuffer.attach() ran at its start.
        assertThat(boot).as("the agent plugin's own enable line").anyMatch(
                line -> line.startsWith("INFO ProofstoneAgent: Proofstone agent listening on port "));
        assertThat(boot).as("the start-up line").anyMatch(line -> line.contains("For help, type"));
        assertThat(boot).as("no line of the test window").noneMatch(line -> line.contains(client.name() + " joined"));
        // Disjointness from logLines() is by record index, proven in ServerLogLinesTest; text can repeat across records.
        // The boundary, pinned (T-M12-06, specs/test-api.md section 3): the agent loads at STARTUP, so what the server logged before it
        // enabled is not here. If a later change reads those lines (for example from latest.log), this fails and the spec and the user
        // guide must change with it, instead of the documented boundary going stale.
        assertThat(boot).as("the server banner is before the agent").noneMatch(line -> line.contains("Starting minecraft server version"));
        assertThat(boot).as("library loading is before the agent").noneMatch(line -> line.contains("Loading libraries"));
        assertThat(boot).as("the agent's own Enabling line is logged by the plugin manager before onEnable")
                .noneMatch(line -> line.contains("Enabling ProofstoneAgent"));
    }
}
