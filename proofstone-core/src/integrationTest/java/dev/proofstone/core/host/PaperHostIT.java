package dev.proofstone.core.host;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.proofstone.core.cell.Cell;
import dev.proofstone.core.cell.CellPlanner;
import dev.proofstone.core.versions.VersionsCatalog;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Needs Docker and an accepted EULA ({@value Eula#VARIABLE}=true). Without the EULA every test is skipped
 * with that reason, so a skipped run is never read as a pass (ADR-0005).
 */
class PaperHostIT {

    private static Cell cell;
    private static Path agentJar;

    @BeforeAll
    static void plan() {
        Path root = Path.of(System.getProperty("proofstone.repoRoot"));
        VersionsCatalog catalog = VersionsCatalog.load(root.resolve("versions.yml"),
                root.resolve("proofstone-protocol/schemas/versions.schema.json"));
        cell = new CellPlanner(catalog).plan("paper", List.of("latest")).get(0);
        agentJar = Path.of(System.getProperty("proofstone.agentJar"));
    }

    private static void requireEula() {
        assumeTrue(Eula.accepted(), "Minecraft EULA not accepted: set " + Eula.VARIABLE + "=true");
    }

    @Test
    void bootsTheNewestPaperRowAndReportsTheAgentCapabilities() {
        requireEula();
        // SEAM: the agent has no /v1/command yet (T-M1-04), so game rules are applied there once it lands.
        PaperHostOptions options = PaperHostOptions.of(agentJar, true).withApplyGameRules(false);

        try (PaperHost host = PaperHost.start(cell, options)) {
            assertThat(host.health().platform()).isEqualTo("paper");
            assertThat(host.health().mcVersion()).isEqualTo(cell.mc());
            assertThat(host.health().capabilities()).isNotNull();
            System.out.println("agent capabilities: " + host.health().capabilities());
        }
    }

    @Test
    void missingArtefactGlobFailsBeforeAnyContainerStarts(@TempDir Path dir) {
        PaperHostOptions options = PaperHostOptions.of(agentJar, true)
                .withArtefacts(dir, List.of("build/libs/*.jar"));

        assertThatThrownBy(() -> PaperHost.start(cell, options))
                .isInstanceOf(PaperHostException.class)
                .hasMessageContaining("build/libs/*.jar");
    }

    @Test
    void serverWithoutTheAgentFailsReadinessWithTheLogTail(@TempDir Path dir) throws IOException {
        requireEula();
        Path badJar = dir.resolve("agent-all.jar");
        try (OutputStream out = Files.newOutputStream(badJar); ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry("not-a-plugin.txt"));
            zip.write(1);
            zip.closeEntry();
        }
        PaperHostOptions options = PaperHostOptions.of(badJar, true).withApplyGameRules(false)
                .withAgentTimeout(java.time.Duration.ofSeconds(10));

        assertThatThrownBy(() -> PaperHost.start(cell, options))
                .isInstanceOf(PaperHostException.class)
                .hasMessageContaining("agent /v1/health OK")
                .hasMessageContaining("log tail");
    }
}
