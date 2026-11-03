package dev.proofstone.conformance;

import static org.assertj.core.api.Assertions.assertThat;

import dev.proofstone.api.Agent;
import dev.proofstone.api.Cell;
import dev.proofstone.api.CellTest;
import dev.proofstone.api.Client;
import dev.proofstone.api.Matrix;
import dev.proofstone.api.Platform;
import dev.proofstone.api.Proofstone;
import java.util.regex.Pattern;

/**
 * The first conformance scenarios (specs/self-tests.md §1), vanilla server with no plugin. C01 and C02 share one
 * server, so the class boots it once.
 */
@Proofstone
@Matrix(platform = Platform.PAPER, versions = {"latest"})
class ConformanceSmokeTest {

    /**
     * C01: the agent answered {@code /v1/health}. The protocol major is checked at readiness: {@code PaperHost}
     * refuses any agent that does not speak {@code 1.x}, so a mismatch fails the class before this runs.
     */
    @CellTest
    void c01AgentHealthAnswersAndTheProtocolMatches(Cell cell, Agent agent) {
        assertThat(cell.platform()).isEqualTo("paper");
        assertThat(agent.capabilities()).isNotEmpty();
    }

    /** C02: a joined client runs {@code /help} and reads the reply back. */
    @CellTest
    void c02HelpAnswersTheClientWithASystemMessage(Client client) {
        client.command("help");
        assertThat(client.awaitMessage(Pattern.compile("Help"))).contains("Help");
    }
}
