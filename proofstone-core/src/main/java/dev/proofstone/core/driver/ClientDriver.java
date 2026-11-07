package dev.proofstone.core.driver;

import java.nio.file.Path;
import org.testcontainers.containers.Network;

/**
 * A Minecraft client the framework drives (client-driver spec §1, ADR-0004).
 *
 * <p>SEAM: edition, capabilities and the protocol descriptor join the SPI when a second driver or windows
 * (M2) need them. The first driver needs only an id and {@link #connect}.
 */
public interface ClientDriver extends AutoCloseable {

    /** Stable driver id, such as {@code mcpl}. */
    String id();

    /** The Minecraft version this driver speaks natively; other servers need a bridge. */
    String protocolVersion();

    /**
     * Logs in with offline auth and returns once the session is in the play state.
     *
     * @throws ClientDriverException when the server refuses, kicks or does not answer within the timeout
     */
    ClientSession connect(ConnectSpec spec);

    /**
     * Binds the driver to a cell's network: a driver that runs a sidecar beside the server (the reference driver)
     * starts it there and returns the bound driver. The result is closed with the cell. A driver that needs nothing
     * on the network returns itself.
     *
     * @param cellNetwork the network the server (and ViaProxy, when bridged) joined
     * @param repoRoot the repository root, where a sidecar image's files live
     */
    default ClientDriver onNetwork(Network cellNetwork, Path repoRoot) {
        return this;
    }

    /** Stops what {@link #onNetwork} started. Nothing by default. */
    @Override
    default void close() {
    }
}
