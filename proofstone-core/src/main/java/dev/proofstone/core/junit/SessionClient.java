package dev.proofstone.core.junit;

import dev.proofstone.api.Client;
import dev.proofstone.core.driver.ClientSession;
import java.time.Duration;
import java.util.regex.Pattern;

/** A {@link Client} over a driver session; closes with the test. */
final class SessionClient implements Client, AutoCloseable {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);

    private final String name;
    private final ClientSession session;

    SessionClient(String name, ClientSession session) {
        this.name = name;
        this.session = session;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public void command(String commandWithoutSlash) {
        session.command(commandWithoutSlash);
    }

    @Override
    public String awaitMessage(Pattern pattern) {
        return session.awaitMessage(pattern, DEFAULT_TIMEOUT).text();
    }

    @Override
    public void close() {
        session.close();
    }
}
