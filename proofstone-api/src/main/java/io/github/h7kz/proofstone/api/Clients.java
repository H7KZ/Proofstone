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
}
