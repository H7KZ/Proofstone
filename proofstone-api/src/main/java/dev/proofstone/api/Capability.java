package dev.proofstone.api;

/**
 * A feature a cell can offer (glossary: Capability). A test declares the ones it needs with {@link Requires}, and a cell
 * that lacks one is SKIPPED with a reason, never a pass (test-api spec section 10).
 *
 * <p>A cell holds a capability only when its platform pack declares it and the agent reports the matching evidence.
 * Versions never imply a capability.
 */
public enum Capability {

    /** Bukkit events fire on the server, so {@link Agent#events()} can observe a plugin's events. */
    BUKKIT_EVENTS,

    /** The server has a console, so {@link Agent#command(String)} runs commands on it. */
    CONSOLE,

    /** The server loads vanilla datapacks. */
    DATAPACKS,

    /** The server exposes its version-specific NMS internals, which change from one Minecraft version to the next. */
    NMS,

    /** Folia's regionised threading: there is no main thread, and each region owns its own data. */
    FOLIA_REGIONS
}
