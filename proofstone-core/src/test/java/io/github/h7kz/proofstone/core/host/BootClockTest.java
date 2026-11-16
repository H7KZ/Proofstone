package io.github.h7kz.proofstone.core.host;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.h7kz.proofstone.core.cell.Cell;
import io.github.h7kz.proofstone.core.cell.TopologyCell;
import io.github.h7kz.proofstone.core.versions.ImagePin;
import io.github.h7kz.proofstone.core.versions.Status;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.WaitStrategy;

/**
 * The harness has one boot clock (T-M8-25). Testcontainers' default wait is a 60 s port wait that killed a healthy but
 * slow Paper boot as {@code Container startup failed} (T-M8-24). No Docker: each container is configured, never started.
 */
class BootClockTest {

    @TempDir
    Path dir;

    private static WaitStrategy waitOf(GenericContainer<?> container) throws ReflectiveOperationException {
        Field field = GenericContainer.class.getDeclaredField("waitStrategy");
        field.setAccessible(true);
        return (WaitStrategy) field.get(container);
    }

    private static Cell paper() {
        return new Cell("paper", "26.3", 244, "ab".repeat(32), "java25", Status.REQUIRED, "itzg/minecraft-server",
                "sha256:" + "cd".repeat(32), List.of());
    }

    @Test
    void aPaperContainerDoesNotWaitOnThePort() throws IOException, ReflectiveOperationException {
        Path agent = Files.write(dir.resolve("agent.jar"), new byte[] {1});
        PaperHost host = new PaperHost(paper(), PaperHostOptions.of(agent, true));
        host.configure(List.of(), null);

        assertThat(waitOf(host.container())).isInstanceOf(NoPortWait.class);
    }

    @Test
    void aProxyContainerDoesNotWaitOnThePort() throws IOException, ReflectiveOperationException {
        Path agent = Files.write(dir.resolve("agent.jar"), new byte[] {1});
        TopologyCell cell = new TopologyCell("velocity@4.3.1+paper@26.3",
                new TopologyCell.Proxy("velocity", "4.3.1", 45, "ef".repeat(32), "java25", "modern", "itzg/mc-proxy",
                        "sha256:" + "12".repeat(32), Status.EXPERIMENTAL),
                paper(), List.of("lobby"), Status.EXPERIMENTAL, Status.EXPERIMENTAL, List.of());
        ProxyHost host = ProxyHost.unstarted(ProxyHostOptions.of(cell, agent));
        host.configure(null, Map.of());

        assertThat(waitOf(host.container())).isInstanceOf(NoPortWait.class);
    }

    @Test
    void aViaProxyContainerDoesNotWaitOnThePort() throws ReflectiveOperationException {
        ViaProxyHost host = new ViaProxyHost(paper(), new ImagePin("ghcr.io/viaversion/viaproxy", "sha256:" + "34".repeat(32)),
                null);

        assertThat(waitOf(host.container())).isInstanceOf(NoPortWait.class);
    }
}
