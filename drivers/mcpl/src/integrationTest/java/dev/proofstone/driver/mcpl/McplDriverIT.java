package dev.proofstone.driver.mcpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.proofstone.core.cell.Cell;
import dev.proofstone.core.cell.CellPlanner;
import dev.proofstone.core.driver.ChatMessage;
import dev.proofstone.core.driver.ClientDriverException;
import dev.proofstone.core.driver.ClientSession;
import dev.proofstone.core.driver.ConnectSpec;
import dev.proofstone.core.host.Eula;
import dev.proofstone.core.host.PaperHost;
import dev.proofstone.core.host.PaperHostOptions;
import dev.proofstone.core.versions.VersionsCatalog;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * Needs Docker and an accepted EULA ({@value Eula#VARIABLE}=true). Without the EULA the test is skipped with
 * that reason, so a skipped run is never read as a pass (ADR-0005).
 */
class McplDriverIT {

    private static final Duration WAIT = Duration.ofSeconds(20);

    @Test
    void joinsTheNewestPaperRowRunsVersionAndReportsADroppedConnection() {
        assumeTrue(Eula.accepted(), "Minecraft EULA not accepted: set " + Eula.VARIABLE + "=true");
        Path root = Path.of(System.getProperty("proofstone.repoRoot"));
        VersionsCatalog catalog = VersionsCatalog.load(root.resolve("versions.yml"),
                root.resolve("proofstone-protocol/schemas/versions.schema.json"));
        Cell cell = new CellPlanner(catalog).plan("paper", List.of("latest")).get(0);
        PaperHostOptions options = PaperHostOptions.of(Path.of(System.getProperty("proofstone.agentJar")), true)
                .withApplyGameRules(false);
        McplDriver driver = new McplDriver();
        assertThat(driver.protocolVersion()).isEqualTo(cell.mc());

        try (PaperHost host = PaperHost.start(cell, options)) {
            ConnectSpec spec = new ConnectSpec(host.gameHost(), host.gamePort(), "ProofBot", Duration.ofSeconds(30));
            try (ClientSession session = driver.connect(spec)) {
                ChatMessage joined = session.awaitMessage(Pattern.compile("joined"), WAIT);
                System.out.println("join message: " + joined);

                session.command("version");
                ChatMessage version = session.awaitMessage(Pattern.compile("Paper"), WAIT);
                System.out.println("version reply: " + version);

                // A dropped connection must fail the wait with a reason, never hang. Stopping the server is the
                // trigger because the agent's command sender cannot run vanilla commands such as kick yet
                // (it throws "Cannot make ProofstoneAgent a vanilla command listener"; agent card follow-up).
                host.close();
                assertThatThrownBy(() -> session.awaitMessage(Pattern.compile("never sent"), WAIT))
                        .isInstanceOf(ClientDriverException.class)
                        .hasMessageContaining("disconnected while waiting");
                System.out.println("transcript: " + session.transcript());
            }
        }
    }
}
