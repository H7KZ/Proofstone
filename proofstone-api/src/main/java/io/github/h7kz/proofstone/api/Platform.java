package io.github.h7kz.proofstone.api;

/** Server platforms a {@link Matrix} can name. */
public enum Platform {
    PAPER,

    /** Folia, Paper's regionised fork: no main thread, each region owns its data (docs/platforms/folia.md). */
    FOLIA,

    /** Purpur, a Paper fork with its own config and gameplay extras (docs/platforms/purpur.md). */
    PURPUR,

    /** Spigot, built from source by BuildTools and cached by SHA-256 (docs/platforms/spigot.md). */
    SPIGOT,

    /**
     * Velocity, a proxy (docs/platforms/velocity.md). A {@link Matrix} naming it plans topology cells: the proxy plus
     * Paper backends, on the pairing {@code versions.yml} lists for {@code paper@latest} (ADR-0016).
     */
    VELOCITY,

    /** BungeeCord, a proxy with legacy forwarding (docs/platforms/bungeecord.md). Plans topology cells like VELOCITY. */
    BUNGEECORD,

    /**
     * Fabric, a mod loader (docs/platforms/fabric.md). A {@link Matrix} naming it plans a loader cell on a
     * {@code versions.yml} loader row (ADR-0017). Loader rows are experimental, so such a cell runs only when
     * {@code -Dproofstone.cells} names it, for example {@code fabric@26.3}.
     */
    FABRIC,

    /** NeoForge, a mod loader (docs/platforms/neoforge.md). Plans loader cells like FABRIC (ADR-0017). */
    NEOFORGE
}
