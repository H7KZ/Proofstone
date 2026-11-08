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
     */
    Set<Capability> capabilities();
}
