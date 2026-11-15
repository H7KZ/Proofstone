package io.github.h7kz.proofstone.core.junit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.h7kz.proofstone.core.cell.Cell;
import io.github.h7kz.proofstone.core.versions.Status;
import io.github.h7kz.proofstone.protocol.Health;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The class-shared host key (T-M7-17): one server per cell, never one per Minecraft version, or the second platform of a
 * {@code PER_CLASS} class runs on the first one's server.
 */
class HostKeyTest {

    private static Cell cell(String platform, String mc, Integer build) {
        return new Cell(platform, mc, build, "ab".repeat(32), "java25", Status.REQUIRED, "itzg/minecraft-server",
                "sha256:" + "cd".repeat(32), List.of());
    }

    @Test
    void cellsOfOneVersionOnDifferentPlatformsGetDifferentHosts() {
        assertThat(CellInvocation.hostKey(cell("paper", "26.3", 244)))
                .isNotEqualTo(CellInvocation.hostKey(cell("folia", "26.3", 12)))
                .isNotEqualTo(CellInvocation.hostKey(cell("purpur", "26.3", 7)))
                .isNotEqualTo(CellInvocation.hostKey(cell("spigot", "26.3", null)));
    }

    @Test
    void oneCellsTestsShareOneHost() {
        assertThat(CellInvocation.hostKey(cell("paper", "26.3", 244)))
                .isEqualTo(CellInvocation.hostKey(cell("paper", "26.3", 244)));
    }

    @Test
    void aBuildOrAVersionChangeIsAnotherHost() {
        assertThat(CellInvocation.hostKey(cell("paper", "26.3", 244)))
                .isNotEqualTo(CellInvocation.hostKey(cell("paper", "26.3", 245)))
                .isNotEqualTo(CellInvocation.hostKey(cell("paper", "1.8.8", 244)));
    }

    @Test
    void aLoaderCellAndABukkitCellOfOneVersionDoNotShare() {
        assertThat(CellInvocation.hostKey(cell("fabric", "26.3", null)))
                .isNotEqualTo(CellInvocation.hostKey(cell("paper", "26.3", 244)));
    }

    @Test
    void aServerOfAnotherPlatformIsRefused() {
        Health paper = new Health("0.1.0", "1.0", "paper", "26.3", List.of());
        assertThatThrownBy(() -> CellInvocation.requirePlatform(cell("folia", "26.3", 12), paper))
                .hasMessageContaining("folia@26.3").hasMessageContaining("paper");
        assertThatCode(() -> CellInvocation.requirePlatform(cell("paper", "26.3", 244), paper)).doesNotThrowAnyException();
    }
}
