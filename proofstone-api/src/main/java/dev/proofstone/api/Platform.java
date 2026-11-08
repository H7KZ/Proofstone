package dev.proofstone.api;

/** Server platforms a {@link Matrix} can name. */
public enum Platform {
    PAPER,

    /** Folia, Paper's regionised fork: no main thread, each region owns its data (docs/platforms/folia.md). */
    FOLIA
}
