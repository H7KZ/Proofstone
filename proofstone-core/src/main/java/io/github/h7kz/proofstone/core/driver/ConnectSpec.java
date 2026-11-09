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
 */
public record ConnectSpec(String host, int port, String username, Duration connectTimeout) {

    public ConnectSpec {
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(username, "username");
        Objects.requireNonNull(connectTimeout, "connectTimeout");
    }
}
