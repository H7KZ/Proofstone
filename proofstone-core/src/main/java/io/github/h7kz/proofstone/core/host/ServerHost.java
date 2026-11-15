package io.github.h7kz.proofstone.core.host;

/**
 * A single-server cell's server, as the JUnit lifecycle uses it: the agent, the address a driver joins and the log tail.
 * {@link PaperHost} (the Bukkit family) and {@link LoaderHost} (Fabric, NeoForge; ADR-0017 section 2) implement it, so
 * the per-test lifecycle, the leak detector and the repro bundle run unchanged on both.
 */
public interface ServerHost extends AgentEndpoint, AutoCloseable {

    /** The Minecraft version the cell runs. */
    String mc();

    /** The host name a driver connects to. */
    String gameHost();

    /** The host port mapped to the server's game port. */
    int gamePort();

    /** The ids the server must list for the data packs this host installed ({@code file/<name>}); empty when none. */
    default java.util.List<String> dataPackIds() {
        return java.util.List.of();
    }

    /** The last lines of the server log, for a failure message. */
    String logTail();

    /** Stops the server's container. */
    @Override
    void close();
}
