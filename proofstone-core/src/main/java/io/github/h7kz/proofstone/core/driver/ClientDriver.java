package io.github.h7kz.proofstone.core.driver;

import java.nio.file.Path;
import org.testcontainers.containers.Network;

/**
 * A Minecraft client the framework drives (client-driver spec §1, ADR-0004).
 *
 * <p>SEAM: capabilities and the protocol descriptor join the SPI when a driver needs them. The edition joined it
 * with the Bedrock driver (ADR-0018).
 */
public interface ClientDriver extends AutoCloseable {

    /** Stable driver id, such as {@code mcpl}. */
    String id();

    /** The Minecraft version this driver speaks natively; other servers need a bridge. */
    String protocolVersion();

    /** The edition this driver speaks (ADR-0018 section 3); it joins endpoints of that edition only. */
    Edition edition();

    /**
     * Logs in with offline auth and returns once the session is in the play state.
     *
     * @throws ClientDriverException when the server refuses, kicks or does not answer within the timeout
     */
    ClientSession connect(ConnectSpec spec);

    /**
     * Joins an endpoint of a known edition. Core calls this, never {@link #connect(ConnectSpec)} alone, so a driver is
     * never pointed at the other edition's port (a Java driver on Geyser's UDP port): that is an error naming both
     * editions, and nothing is connected (ADR-0018 section 3).
     *
     * @param endpoint the edition the address in {@code spec} serves
     * @throws ClientDriverException when the driver's edition is not {@code endpoint}
     */
    default ClientSession connect(ConnectSpec spec, Edition endpoint) {
        if (edition() != endpoint) {
            throw new ClientDriverException("driver " + id() + " speaks " + edition() + " and cannot join the "
                    + endpoint + " endpoint " + spec.host() + ":" + spec.port() + ": a cell offers one driver per "
                    + "edition (ADR-0018 section 3)");
        }
        return connect(spec);
    }

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
