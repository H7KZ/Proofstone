package io.github.h7kz.proofstone.core.driver;

/**
 * The Minecraft edition a client driver speaks, or an endpoint serves (ADR-0018 section 3). Internal to core: it is not
 * public API, and no test sees it. A driver and the endpoint it joins must have the same edition; see
 * {@link ClientDriver#connect(ConnectSpec, Edition)}.
 */
public enum Edition {
    /** Java Edition: the {@code mcpl} and {@code mineflayer-ref} drivers, and every Paper, proxy and loader port. */
    JAVA,
    /** Bedrock Edition: the Bedrock driver, and Geyser's UDP port. */
    BEDROCK
}
