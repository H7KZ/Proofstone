package dev.proofstone.driver.mineflayer;

import dev.proofstone.core.driver.ClientDriver;
import dev.proofstone.core.driver.ClientDriverException;
import dev.proofstone.core.driver.ClientSession;
import dev.proofstone.core.driver.ConnectSpec;
import java.net.URI;
import java.nio.file.Path;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;

/**
 * The reference driver (ADR-0004): Mineflayer in a sidecar container, reached over JSON-RPC on a WebSocket. It is
 * used only for differential runs on rows where it is native, never as the primary driver.
 *
 * <p>{@link #onNetwork} builds the sidecar image from {@code images/mineflayer-ref}, copies it in (no bind mounts,
 * ADR-0005) and starts it on the cell network. The returned driver is bound to that sidecar. {@link ConnectSpec#host()}
 * and the port are as the sidecar sees them: the server's alias, or ViaProxy's on a bridged cell.
 */
public final class MineflayerRefDriver implements ClientDriver {

    /**
     * The native row: Mineflayer 4.39.0 speaks protocol 775, which versions.yml lists as row 26.1.2
     * (drivers.mineflayer-ref max-native 26.1). Core bridges any other row, because it compares this string with
     * the row's Minecraft version.
     */
    static final String PROTOCOL_VERSION = "26.1.2";

    /** The sidecar's WebSocket port, as {@code images/mineflayer-ref/server.js} listens on it. */
    static final int SIDECAR_PORT = 8765;

    private final URI sidecar;
    private final GenericContainer<?> container;

    /** Found through ServiceLoader; unbound until {@link #onNetwork} starts its sidecar on a cell. */
    public MineflayerRefDriver() {
        this(null, null);
    }

    /** An unbound driver, for a test that talks to a sidecar it started itself. */
    public MineflayerRefDriver(URI sidecar) {
        this(sidecar, null);
    }

    private MineflayerRefDriver(URI sidecar, GenericContainer<?> container) {
        this.sidecar = sidecar;
        this.container = container;
    }

    @Override
    public String id() {
        return "mineflayer-ref";
    }

    @Override
    public String protocolVersion() {
        return PROTOCOL_VERSION;
    }

    @Override
    public ClientDriver onNetwork(Network cellNetwork, Path repoRoot) {
        ImageFromDockerfile image = new ImageFromDockerfile()
                .withFileFromPath(".", repoRoot.resolve("images/mineflayer-ref"));
        GenericContainer<?> started = new GenericContainer<>(image)
                .withNetwork(cellNetwork)
                .withExposedPorts(SIDECAR_PORT)
                .waitingFor(Wait.forListeningPort())
                // A sidecar crash shows up only as a dropped socket, so its own output goes to the test log.
                .withLogConsumer(frame -> System.out.print("[sidecar] " + frame.getUtf8String()));
        started.start();
        URI uri = URI.create("ws://" + started.getHost() + ":" + started.getMappedPort(SIDECAR_PORT));
        return new MineflayerRefDriver(uri, started);
    }

    @Override
    public ClientSession connect(ConnectSpec spec) {
        if (sidecar == null) {
            throw new ClientDriverException("the reference driver is not bound to a sidecar: call onNetwork first");
        }
        return MineflayerRefSession.open(sidecar, spec);
    }

    @Override
    public void close() {
        if (container != null) {
            container.close();
        }
    }
}
