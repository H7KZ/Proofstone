package io.github.h7kz.proofstone.core.junit;

import io.github.h7kz.proofstone.api.Capability;
import io.github.h7kz.proofstone.api.Client;
import io.github.h7kz.proofstone.api.Clients;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * The {@link Clients} a test gets (test-api spec section 11). It validates the name before anything connects, joins
 * through the cell's driver, and keeps every client it made so the test's teardown disconnects them all.
 */
final class TestClients implements Clients {

    /** Vanilla usernames: 1 to 16 characters, with an optional {@code .} prefix that the simulated Bedrock tier uses. */
    private static final Pattern USERNAME = Pattern.compile("\\.?[A-Za-z0-9_]{1,15}");

    /** A Bedrock name: Floodgate prepends the {@code .}, so the test's own name has none and is 1 to 15 characters. */
    private static final Pattern BEDROCK_NAME = Pattern.compile("[A-Za-z0-9_]{1,15}");

    /** How a Geyser cell joins a Bedrock client and waits for the server to forget it (ADR-0018 sections 7 and 9). */
    interface Bedrock {
        /** Joins {@code name}, returning the client under its server-side name once the agent lists it. */
        SessionClient join(String name);

        /** Waits, on the agent, until the server no longer lists {@code serverName}; never a sleep. */
        void awaitGone(String serverName);
    }

    /** A client this test made, and what to wait for after its session closed. */
    private record Created(SessionClient client, Runnable afterClose) {
    }

    private final Function<String, SessionClient> connector;
    private final String cellId;
    private final Set<Capability> capabilities;
    /** {@code null} on a cell that is not a Geyser cell. */
    private final Bedrock bedrock;
    private final List<Created> created = new ArrayList<>();

    /** Clients of a cell that holds no Bedrock capability: {@link #bedrock} is refused there. */
    TestClients(Function<String, SessionClient> connector) {
        this(connector, "this cell", Set.of(), null);
    }

    TestClients(Function<String, SessionClient> connector, String cellId, Set<Capability> capabilities,
            Bedrock bedrock) {
        this.connector = connector;
        this.cellId = cellId;
        this.capabilities = Set.copyOf(capabilities);
        this.bedrock = bedrock;
    }

    @Override
    public Client create(String name) {
        if (name == null || !USERNAME.matcher(name).matches()) {
            throw new IllegalArgumentException("not a valid username: \"" + name + "\" (1 to 16 characters from"
                    + " A-Z a-z 0-9 _, optionally one leading '.' for a prefixed name)");
        }
        SessionClient client = connector.apply(name);
        created.add(new Created(client, null));
        return client;
    }

    @Override
    public Client bedrock(String name) {
        if (name == null || !BEDROCK_NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("not a valid Bedrock name: \"" + name + "\" (1 to 15 characters from"
                    + " A-Z a-z 0-9 _, no '.': Floodgate adds the prefix)");
        }
        // Held means Geyser cell, native, one server and the agent reported floodgate. Anything else is refused: a bridged,
        // loader or topology cell never holds it, and a test that declared the requirement was skipped before this call.
        if (bedrock == null || !capabilities.contains(Capability.BEDROCK_CLIENT)) {
            throw new IllegalStateException("clients.bedrock(\"" + name + "\") needs the capability BEDROCK_CLIENT, which "
                    + "cell " + cellId + " does not hold: plan a Geyser cell with @Matrix(platform = Platform.GEYSER) "
                    + "and declare @Requires(Capability.BEDROCK_CLIENT) so a cell without it is skipped, not failed "
                    + "(ADR-0018 sections 3 and 9)");
        }
        SessionClient client = bedrock.join(name);
        created.add(new Created(client, () -> bedrock.awaitGone(client.name())));
        return client;
    }

    /** The names of the clients this test created and has not closed yet. */
    List<String> names() {
        return created.stream().map(c -> c.client().name()).toList();
    }

    /**
     * Disconnects every client this test created. Every close runs, whatever an earlier one did.
     *
     * @return the first failure, with the others suppressed, or {@code null} when all closed
     */
    RuntimeException closeAll() {
        RuntimeException failure = null;
        for (Created entry : created) {
            SessionClient client = entry.client();
            try {
                client.close();
                if (entry.afterClose() != null) {
                    entry.afterClose().run();
                }
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
