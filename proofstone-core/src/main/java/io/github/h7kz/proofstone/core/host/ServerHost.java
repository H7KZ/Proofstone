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

    /** The mod ids the agent must list for the mods this host installed ({@code GET /v1/mods}); empty when none. */
    default java.util.List<String> modIds() {
        return java.util.List.of();
    }

    /** The ids the server must list for the data packs this host installed ({@code file/<name>}); empty when none. */
    default java.util.List<String> dataPackIds() {
        return java.util.List.of();
    }

    /**
     * The namespaces ({@code data/<namespace>}) of each installed data pack, by its id: how a server log line about a
     * function or a recipe is attributed to a pack (T-M8-11). Empty when none is installed.
     */
    default java.util.Map<String, java.util.List<String>> dataPackNamespaces() {
        return java.util.Map.of();
    }

    /** The last lines of the server log, for a failure message. */
    String logTail();

    /** Stops the server's container. */
    @Override
    void close();
}
