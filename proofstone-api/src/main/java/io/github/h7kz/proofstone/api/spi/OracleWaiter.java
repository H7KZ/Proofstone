package io.github.h7kz.proofstone.api.spi;

import java.time.Duration;
import java.util.function.BiPredicate;
import java.util.function.Supplier;

/**
 * How {@code DualAssert} waits. The API module cannot wait itself (waiting on wall-clock time is allowed in one class
 * of the framework only, ADR-0007), so {@code proofstone-core} provides this through a service file and delegates
 * to its await helper. Not for test authors.
 */
public interface OracleWaiter {

    /**
     * Polls both suppliers until {@code ok} accepts both values together, or the timeout passes. It never throws for a
     * timeout: the outcome carries what each side last showed.
     */
    <S, C> Outcome<S, C> untilBoth(String description, Supplier<S> server, Supplier<C> client,
            BiPredicate<S, C> ok, Duration timeout);

    /** What one oracle last showed. {@code error} is the last probe failure, or {@code null}. */
    record Side<T>(boolean present, T value, String error) {
    }

    /** The result of a wait: whether it matched, how long it took, and the last state of each side. */
    record Outcome<S, C>(boolean matched, Duration elapsed, Side<S> server, Side<C> client) {
    }
}
