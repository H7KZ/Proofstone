package io.github.h7kz.proofstone.api;

/**
 * Joins extra Java clients under names the test chooses (test-api spec section 11). A test gets one by declaring a
 * {@code Clients} parameter; the clients it creates are disconnected after the test, like its own {@link Client}.
 *
 * <p>The name is a plain offline-mode username. A prefixed name such as {@code .Steve} is how the simulated Bedrock
 * tier works: it needs {@link Capability#SIMULATED_BEDROCK}, and a test that uses it must declare that requirement.
 */
public interface Clients {

    /**
     * Logs a Java client in under {@code name} and returns it once it is in the play state.
     *
     * @param name 1 to 16 characters from {@code A-Z a-z 0-9 _}, optionally starting with {@code .} (a prefixed name)
     * @throws IllegalArgumentException when the name is not a valid username, before anything connects
     * @throws RuntimeException when the server refuses or kicks the login, with the reason
     */
    Client create(String name);

    /**
     * Joins a real Bedrock client through Geyser and returns it once it has spawned and the server's agent lists it (test-api
     * spec section 14). Floodgate prepends one {@code .} to the name, so the returned client's {@link Client#name()} is
     * {@code "." + name}, and {@code agent.player(client)} finds it. The cell must hold {@link Capability#BEDROCK_CLIENT}:
     * declare {@code @Requires(Capability.BEDROCK_CLIENT)} so a cell without it is skipped with the capability named. A
     * Bedrock client supports chat, commands, messages, inventory and position; other methods throw
     * {@link UnsupportedOperationException}. Not {@link Capability#SIMULATED_BEDROCK}, which is a Java client under a prefixed name.
     *
     * <p>The default throws, so an implementation that predates Bedrock still compiles and fails loudly; the core overrides it.
     *
     * @param name 1 to 15 characters from {@code A-Z a-z 0-9 _}, without a {@code .}
     * @throws IllegalArgumentException when the name is not valid, before anything connects
     * @throws IllegalStateException when the cell does not hold {@code BEDROCK_CLIENT} (a test that declared the requirement
     *     is skipped before it gets here)
     * @throws UnsupportedOperationException in an implementation that does not override this method
     */
    default Client bedrock(String name) {
        throw new UnsupportedOperationException("this Clients implementation cannot join a Bedrock client "
                + "(Capability.BEDROCK_CLIENT); override bedrock");
    }
}
