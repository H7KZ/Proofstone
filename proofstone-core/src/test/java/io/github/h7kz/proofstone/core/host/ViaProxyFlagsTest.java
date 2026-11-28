package io.github.h7kz.proofstone.core.host;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.h7kz.proofstone.core.cell.Cell;
import io.github.h7kz.proofstone.core.versions.ImagePin;
import io.github.h7kz.proofstone.core.versions.Status;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * ViaProxy compresses its proxy-to-client leg at 256 bytes by default. That runs a second disable/restore pair of auto-read on its server
 * channel at login, and a data race between the two pairs stalls about 1 join in 100 (T-M12-15, T-M12-17). The leg is inside the cell
 * network, so the container starts with {@code --compression-threshold -1}. No Docker: the container is configured, never started.
 */
class ViaProxyFlagsTest {

    @Test
    void viaProxyStartsWithoutProxyToClientCompressionSoTheAutoReadPairsCannotOverlap() {
        Cell cell = new Cell("paper", "1.21.11", 130, "ab".repeat(32), "java21", Status.REQUIRED, "itzg/minecraft-server",
                "sha256:" + "cd".repeat(32), List.of());
        ViaProxyHost host = new ViaProxyHost(cell, new ImagePin("ghcr.io/viaversion/viaproxy", "sha256:" + "34".repeat(32)), null);

        List<String> command = List.of(host.container().getCommandParts());

        int at = command.indexOf("--compression-threshold");
        assertThat(at).as("the flag is passed: %s", command).isGreaterThanOrEqualTo(0);
        assertThat(command.get(at + 1)).isEqualTo("-1");
    }
}
