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
    BUNGEECORD
}
