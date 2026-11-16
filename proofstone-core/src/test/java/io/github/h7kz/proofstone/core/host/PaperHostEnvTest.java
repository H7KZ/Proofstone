package io.github.h7kz.proofstone.core.host;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.h7kz.proofstone.core.cell.Cell;
import io.github.h7kz.proofstone.core.versions.Status;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The image fetches Paper's default configs from an unpinned GitHub branch on every boot unless told not to (T-M8-19).
 * No Docker: the container is configured, never started.
 */
class PaperHostEnvTest {

    @TempDir
    Path dir;

    private static Cell cell(String platform) {
        return new Cell(platform, "26.3", 244, "ab".repeat(32), "java25", Status.REQUIRED, "itzg/minecraft-server",
                "sha256:" + "cd".repeat(32), List.of());
    }

    private Map<String, String> env(String platform, Path serverJar) throws IOException {
        Path agent = Files.write(dir.resolve("agent.jar"), new byte[] {1});
        PaperHost host = new PaperHost(cell(platform), PaperHostOptions.of(agent, true));
        host.configure(List.of(), serverJar);
        return host.containerEnv();
    }

    @Test
    void paperDoesNotLetTheImageFetchDefaultConfigs() throws IOException {
        assertThat(env("paper", null)).containsEntry("SKIP_DOWNLOAD_DEFAULTS", "true")
                .doesNotContainKeys("PAPER_CONFIG_DEFAULTS_REPO", "PAPER_CONFIG_REPO");
    }

    @Test
    void foliaGoesThroughTheSameDeployScriptSoItIsTheSame() throws IOException {
        assertThat(env("folia", null)).containsEntry("SKIP_DOWNLOAD_DEFAULTS", "true");
    }

    @Test
    void aBuiltSpigotJarIsTheSame() throws IOException {
        Path jar = Files.write(dir.resolve("spigot.jar"), new byte[] {2});
        assertThat(env("spigot", jar)).containsEntry("SKIP_DOWNLOAD_DEFAULTS", "true");
    }
}
