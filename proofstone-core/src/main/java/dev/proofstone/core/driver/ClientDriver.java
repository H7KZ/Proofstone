package dev.proofstone.core.driver;

/**
 * A Minecraft client the framework drives (client-driver spec §1, ADR-0004).
 *
 * <p>SEAM: edition, capabilities and the protocol descriptor join the SPI when a second driver or windows
 * (M2) need them. The first driver needs only an id and {@link #connect}.
 */
public interface ClientDriver {

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
}
