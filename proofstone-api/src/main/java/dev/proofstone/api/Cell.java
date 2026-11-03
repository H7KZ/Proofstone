package dev.proofstone.api;

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
}
