package dev.proofstone.api;

/**
 * Thrown by {@link DualAssert} when exactly one oracle matched the expectation (ADR-0003). It is a possible desync or
 * harness bug, so it is reported apart from a plain failure. The message shows both observed values.
 */
public final class DisagreeException extends AssertionError {

    private static final long serialVersionUID = 1L;

    private final String serverObserved;
    private final String clientObserved;

    public DisagreeException(String message, String serverObserved, String clientObserved) {
        super(message);
        this.serverObserved = serverObserved;
        this.clientObserved = clientObserved;
    }

    /** What the server oracle (agent) showed. */
    public String serverObserved() {
        return serverObserved;
    }

    /** What the client oracle (driver) showed. */
    public String clientObserved() {
        return clientObserved;
    }
}
