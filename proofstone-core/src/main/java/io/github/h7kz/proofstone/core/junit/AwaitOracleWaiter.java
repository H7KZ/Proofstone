package io.github.h7kz.proofstone.core.junit;

import io.github.h7kz.proofstone.api.spi.OracleWaiter;
import io.github.h7kz.proofstone.core.await.Await;
import io.github.h7kz.proofstone.core.await.AwaitTimeoutException;
import io.github.h7kz.proofstone.core.await.AwaitTimeoutException.Observation;
import java.time.Duration;
import java.util.function.BiPredicate;
import java.util.function.Supplier;

/** The API's {@link OracleWaiter} over {@link Await#untilBoth}, registered by a service file. */
public final class AwaitOracleWaiter implements OracleWaiter {

    @Override
    public <S, C> Outcome<S, C> untilBoth(String description, Supplier<S> server, Supplier<C> client,
            BiPredicate<S, C> ok, Duration timeout) {
        long start = System.nanoTime();
        Outcome<S, C> outcome;
        try {
            Await.Both<S, C> both = Await.untilBoth(description, server, client, ok, timeout);
            outcome = new Outcome<>(true, Duration.ofNanos(System.nanoTime() - start),
                    new Side<>(true, both.server(), null), new Side<>(true, both.client(), null));
        } catch (AwaitTimeoutException e) {
            outcome = new Outcome<>(false, e.elapsed(), side(e, "server"), side(e, "client"));
        }
        // The assertion point of a differential run (self-tests section 3): the client's last view, matched or not.
        DifferentialRun.record(description, outcome.client());
        return outcome;
    }

    @SuppressWarnings("unchecked")
    private static <T> Side<T> side(AwaitTimeoutException e, String label) {
        for (Observation observation : e.observations()) {
            if (observation.label().equals(label)) {
                return new Side<>(observation.hasValue(), observation.hasValue() ? (T) observation.lastValue() : null,
                        observation.lastException() == null ? null : observation.lastException().toString());
            }
        }
        throw new IllegalStateException("await reported no " + label + " observation: " + e.getMessage());
    }
}
