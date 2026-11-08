package dev.proofstone.api;

import java.util.Set;

/** The environment of one test invocation (test-api spec §2). */
public interface Cell {

    /** Platform id, for example {@code paper}. */
    String platform();

    /** Minecraft version, for example {@code 26.3}. */
    String version();

    /** Java image tag the server runs on, for example {@code java25}. */
    String javaTag();

    /** True when the client reaches the server through a protocol bridge. */
    boolean bridged();

    /**
     * The capabilities this cell holds: the ones its platform pack declares that the agent reports (test-api spec section
     * 10). A test's {@link Requires} is checked against this set before the body runs.
     *
     * <p>The default throws, so a custom {@code Cell} fails loudly on the first test that uses {@link Requires} instead of
     * skipping or passing silently. The core implementation overrides it.
     *
     * @throws UnsupportedOperationException when the implementation does not declare its capabilities
     */
    default Set<Capability> capabilities() {
        throw new UnsupportedOperationException(
                "this Cell implementation does not declare its capabilities; override capabilities()");
    }
}
