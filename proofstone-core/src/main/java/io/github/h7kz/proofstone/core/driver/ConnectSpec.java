package io.github.h7kz.proofstone.core.driver;

import java.time.Duration;
import java.util.Objects;

/**
 * Where and how to join.
 *
 * @param host server host
 * @param port server port
 * @param username offline-mode player name
 * @param connectTimeout how long to wait for the play state; exceeding it is an error
 * @param resourcePacks how a resource-pack push is answered (T-M8-10); {@link ResourcePackPolicy#NONE} by default
 */
public record ConnectSpec(String host, int port, String username, Duration connectTimeout,
        ResourcePackPolicy resourcePacks) {

    public ConnectSpec {
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(username, "username");
        Objects.requireNonNull(connectTimeout, "connectTimeout");
        Objects.requireNonNull(resourcePacks, "resourcePacks");
    }

    /** A join with no resource-pack answer declared: any push ends it loudly (ADR-0017 section 7). */
    public ConnectSpec(String host, int port, String username, Duration connectTimeout) {
        this(host, port, username, connectTimeout, ResourcePackPolicy.NONE);
    }
}
