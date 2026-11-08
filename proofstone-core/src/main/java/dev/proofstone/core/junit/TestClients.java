package dev.proofstone.core.junit;

import dev.proofstone.api.Client;
import dev.proofstone.api.Clients;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * The {@link Clients} a test gets (test-api spec section 11). It validates the name before anything connects, joins
 * through the cell's driver, and keeps every client it made so the test's teardown disconnects them all.
 */
final class TestClients implements Clients {

    /** Vanilla usernames: 1 to 16 characters, with an optional {@code .} prefix that the simulated Bedrock tier uses. */
    private static final Pattern USERNAME = Pattern.compile("\\.?[A-Za-z0-9_]{1,15}");

    private final Function<String, SessionClient> connector;
    private final List<SessionClient> created = new ArrayList<>();

    TestClients(Function<String, SessionClient> connector) {
        this.connector = connector;
    }

    @Override
    public Client create(String name) {
        if (name == null || !USERNAME.matcher(name).matches()) {
            throw new IllegalArgumentException("not a valid username: \"" + name + "\" (1 to 16 characters from"
                    + " A-Z a-z 0-9 _, optionally one leading '.' for a prefixed name)");
        }
        SessionClient client = connector.apply(name);
        created.add(client);
        return client;
    }

    /**
     * Disconnects every client this test created. Every close runs, whatever an earlier one did.
     *
     * @return the first failure, with the others suppressed, or {@code null} when all closed
     */
    RuntimeException closeAll() {
        RuntimeException failure = null;
        for (SessionClient client : created) {
            try {
                client.close();
            } catch (RuntimeException e) {
                if (failure == null) {
                    failure = new IllegalStateException("cannot disconnect the client " + client.name(), e);
                } else {
                    failure.addSuppressed(e);
                }
            }
        }
        created.clear();
        return failure;
    }
}
