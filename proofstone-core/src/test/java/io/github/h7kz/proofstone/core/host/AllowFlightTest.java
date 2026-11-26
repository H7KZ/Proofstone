package io.github.h7kz.proofstone.core.host;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.h7kz.proofstone.core.cell.Cell;
import io.github.h7kz.proofstone.core.versions.Status;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A client held in the air for 80 ticks (for example while its chunk is generated on the first join of a cold world) is kicked by the
 * vanilla anti-fly check, and the error-log guard turns "was kicked for floating too long!" into a failure (T-M12-14, folia@26.3
 * in Disenchantment's repeat series). Every Paper-family cell allows flight. No Docker: the container is configured, never started.
 */
class AllowFlightTest {

    @TempDir
    Path dir;

    @Test
    void everyPaperFamilyCellAllowsFlightSoNoClientIsKickedForFloating() throws IOException {
        Path agent = Files.write(dir.resolve("agent.jar"), new byte[] {1});
        for (String platform : List.of("paper", "folia", "spigot", "purpur")) {
            Cell cell = new Cell(platform, "26.3", 244, "ab".repeat(32), "java25", Status.REQUIRED, "itzg/minecraft-server",
                    "sha256:" + "cd".repeat(32), List.of());
            PaperHost host = new PaperHost(cell, PaperHostOptions.of(agent, true));
            // Spigot is a built jar (no *_BUILD variable), the others are downloaded by the image
            host.configure(List.of(), "spigot".equals(platform) ? Files.write(dir.resolve("spigot.jar"), new byte[] {2}) : null);
            assertThat(host.container().getEnvMap()).as(platform).containsEntry("ALLOW_FLIGHT", "TRUE");
        }
    }
}
