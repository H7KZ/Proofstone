package io.github.h7kz.proofstone.conformance;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.h7kz.proofstone.api.Agent;
import io.github.h7kz.proofstone.api.Cell;
import io.github.h7kz.proofstone.api.CellTest;
import io.github.h7kz.proofstone.api.Client;
import io.github.h7kz.proofstone.api.Matrix;
import io.github.h7kz.proofstone.api.Platform;
import io.github.h7kz.proofstone.api.Proofstone;
import java.util.regex.Pattern;

/**
 * The first conformance scenarios (specs/self-tests.md §1), vanilla server with no plugin. C01 and C02 share one
 * server, so the class boots it once.
 */
@Proofstone
@Matrix(platform = Platform.PAPER, versions = {"latest"})
@Matrix(platform = Platform.FOLIA, versions = {"latest"})
@Matrix(platform = Platform.PURPUR, versions = {"latest"})
@Matrix(platform = Platform.SPIGOT, versions = {"latest"})
// Loader rows are experimental, so they run only when -Dproofstone.cells names them (fabric@26.3), T-M8-08.
@Matrix(platform = Platform.FABRIC, versions = {"26.3"})
@Matrix(platform = Platform.NEOFORGE, versions = {"26.3"})
class ConformanceSmokeTest {

    /**
     * C01: the agent answered {@code /v1/health}. The protocol major is checked at readiness: {@code PaperHost}
     * refuses any agent that does not speak {@code 1.x}, so a mismatch fails the class before this runs.
     */
    @CellTest
    void c01AgentHealthAnswersAndTheProtocolMatches(Cell cell, Agent agent) {
        assertThat(cell.platform()).isIn("paper", "folia", "purpur", "spigot", "fabric", "neoforge");
        assertThat(agent.capabilities()).isNotEmpty();
    }

    /**
     * C02: a joined client runs {@code /help} and reads the reply back. The Bukkit family prints a "Help" header; a vanilla
     * loader server has its own {@code /help} and answers with one system message per command usage, one of which
     * names {@code /help [<command>]} itself (docs/platforms/fabric.md and neoforge.md, Gotchas). Each is asserted as it is.
     */
    @CellTest
    void c02HelpAnswersTheClientWithASystemMessage(Cell cell, Client client) {
        boolean loader = cell.platform().equals("fabric") || cell.platform().equals("neoforge");
        client.command("help");
        String expected = loader ? "/help [<command>]" : "Help";
        assertThat(client.awaitMessage(Pattern.compile(Pattern.quote(expected)))).contains(expected);
    }
}
