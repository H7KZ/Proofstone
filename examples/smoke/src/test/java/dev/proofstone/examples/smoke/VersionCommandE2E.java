package dev.proofstone.examples.smoke;

import static org.assertj.core.api.Assertions.assertThat;

import dev.proofstone.api.Agent;
import dev.proofstone.api.Cell;
import dev.proofstone.api.CellTest;
import dev.proofstone.api.Client;
import dev.proofstone.api.Matrix;
import dev.proofstone.api.Platform;
import dev.proofstone.api.Proofstone;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The walking skeleton: a client joins the newest Paper row and runs {@code /version}. The only pinned Paper
 * row today is the newest, so the matrix says {@code latest}.
 */
@Proofstone
@Matrix(platform = Platform.PAPER, versions = {"latest"})
class VersionCommandE2E {

    // Every test method in the class joins the same server, each with its own client name.
    private static final Set<String> NAMES = new HashSet<>();

    @CellTest
    void versionCommandAnswersWithThePlatformName(Cell cell, Agent agent, Client client) {
        assertThat(cell.platform()).isEqualTo("paper");
        assertThat(agent.capabilities()).isNotEmpty();

        client.command("version");

        assertThat(client.awaitMessage(Pattern.compile("Paper"))).contains(cell.version());
        remember(agent, client);
    }

    @CellTest
    void secondTestHasItsOwnClientOnTheSameServer(Client client, Agent agent) {
        // A plain player may run /plugins but not the op-only vanilla /list; the console (agent) may.
        client.command("plugins");
        assertThat(client.awaitMessage(Pattern.compile("ProofstoneAgent"))).contains("ProofstoneAgent");
        assertThat(agent.command("list")).contains(client.name());
        remember(agent, client);
    }

    /**
     * Each test leaves a scoreboard objective on the server. The list must hold the objective of every test that
     * ran before, so tests that share one server see each other's, whatever order they run in.
     */
    private static void remember(Agent agent, Client client) {
        assertThat(client.name()).matches("T[0-9a-f]{8}");
        assertThat(NAMES.add(client.name())).as("client name %s is reused", client.name()).isTrue();
        agent.command("scoreboard objectives add o" + client.name() + " dummy");
        assertThat(agent.command("scoreboard objectives list"))
                .contains(NAMES.stream().map(name -> "o" + name).toArray(String[]::new));
    }
}
