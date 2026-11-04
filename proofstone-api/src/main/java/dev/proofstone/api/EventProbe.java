package dev.proofstone.api;

import java.util.List;

/**
 * A read-only listener on one server event class (test-api spec section 3). It records, at MONITOR priority, and never
 * changes the event (ADR-0002). Close it to unsubscribe.
 */
public interface EventProbe extends AutoCloseable {

    /**
     * Waits for the first captured event, for the default timeout.
     *
     * @throws RuntimeException on timeout, with the event class
     */
    CapturedEvent awaitOne();

    /** Every event captured so far, oldest first (the agent keeps the most recent ones). */
    List<CapturedEvent> events();

    /** Unsubscribes the probe on the server. */
    @Override
    void close();
}
