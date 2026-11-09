package io.github.h7kz.proofstone.core.host;

import io.github.h7kz.proofstone.core.await.Await;
import io.github.h7kz.proofstone.core.await.AwaitTimeoutException;
import io.github.h7kz.proofstone.core.cell.Cell;
import io.github.h7kz.proofstone.core.versions.ImagePin;
import java.io.IOException;
import java.time.Duration;
import java.util.Objects;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.utility.DockerImageName;

/**
 * The ViaProxy container of a bridged cell (ADR-0004, client-driver spec section 5). It sits on the cell network,
 * forwards to the server by its alias and translates the driver's newest protocol to the server's, so the server
 * stays free of Via plugins. The image is pinned by digest, and it takes no input files: everything is a command
 * line flag (docs/platforms/viaproxy.md).
 */
public final class ViaProxyHost implements AutoCloseable {

    /** The port ViaProxy binds inside its container. */
    public static final int PROXY_PORT = 25568;
    private static final int TAIL_LINES = 60;
    private static final Duration LISTEN_TIMEOUT = Duration.ofSeconds(60);

    private final Cell cell;
    private final GenericContainer<?> container;
    private final LogBuffer logs = new LogBuffer();

    private ViaProxyHost(Cell cell, ImagePin image, Network network) {
        this.cell = cell;
        this.container = new GenericContainer<>(DockerImageName.parse(image.reference()))
                .withNetwork(network)
                .withNetworkAliases("viaproxy")
                .withExposedPorts(PROXY_PORT)
                // The image's entrypoint reads a config file; the cli mode takes flags instead, so nothing is copied.
                .withCreateContainerCmdModifier(cmd -> cmd.withEntrypoint("java", "-jar", "/app/ViaProxy.jar"))
                .withCommand("cli",
                        "--bind-address", "0.0.0.0:" + PROXY_PORT,
                        "--target-address", PaperHost.NETWORK_ALIAS + ":" + PaperHost.GAME_PORT,
                        "--target-version", cell.mc(),
                        "--auth-method", "NONE")
                .withLogConsumer(frame -> logs.append(frame.getUtf8String()));
    }

    /**
     * Starts ViaProxy for a cell whose server is already running on {@code network} under
     * {@link PaperHost#NETWORK_ALIAS}. Returns once ViaProxy listens, or throws with the log tail.
     */
    public static ViaProxyHost start(Cell cell, ImagePin image, Network network) {
        Objects.requireNonNull(cell, "cell");
        ViaProxyHost host = new ViaProxyHost(cell, image, network);
        try {
            host.container.start();
            host.awaitListening();
        } catch (RuntimeException e) {
            String tail = host.logs.tail(TAIL_LINES);
            host.close();
            throw new PaperHostException("ViaProxy for " + cell.platform() + "@" + cell.mc() + " failed to start: "
                    + e.getMessage() + "\n--- viaproxy log tail ---\n" + tail, e);
        }
        return host;
    }

    /** The log line comes before the bind, so readiness is the kernel's listening socket table (state 0A). */
    private void awaitListening() {
        String hexPort = String.format("%04X", PROXY_PORT);
        String probe = "grep -qiE ':" + hexPort + " [0-9A-F:]+ 0A ' /proc/net/tcp /proc/net/tcp6";
        try {
            Await.until("ViaProxy listening on " + PROXY_PORT, () -> {
                if (!container.isRunning()) {
                    throw new IllegalStateException("ViaProxy container exited");
                }
                try {
                    return container.execInContainer("sh", "-c", probe).getExitCode() == 0;
                } catch (IOException e) {
                    throw new IllegalStateException("cannot probe ViaProxy", e);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("probe of ViaProxy interrupted", e);
                }
            }, Boolean::booleanValue, LISTEN_TIMEOUT);
        } catch (AwaitTimeoutException e) {
            throw new PaperHostException("timed out waiting for ViaProxy to listen: " + e.getMessage(), e);
        }
    }

    /** The host name a driver connects to. */
    public String gameHost() {
        return container.getHost();
    }

    /** The host port mapped to ViaProxy's bind port. */
    public int gamePort() {
        return container.getMappedPort(PROXY_PORT);
    }

    public LogBuffer logs() {
        return logs;
    }

    @Override
    public void close() {
        container.stop();
    }
}
