package dev.proofstone.core.junit;

import dev.proofstone.api.spi.OracleWaiter;
import dev.proofstone.core.await.Await;
import dev.proofstone.core.await.AwaitTimeoutException;
import dev.proofstone.core.await.AwaitTimeoutException.Observation;
import java.time.Duration;
import java.util.function.BiPredicate;
import java.util.function.Supplier;

/** The API's {@link OracleWaiter} over {@link Await#untilBoth}, registered by a service file. */
public final class AwaitOracleWaiter implements OracleWaiter {

    @Override
    public <S, C> Outcome<S, C> untilBoth(String description, Supplier<S> server, Supplier<C> client,
            BiPredicate<S, C> ok, Duration timeout) {
        long start = System.nanoTime();
        try {
            Await.Both<S, C> both = Await.untilBoth(description, server, client, ok, timeout);
            return new Outcome<>(true, Duration.ofNanos(System.nanoTime() - start),
                    new Side<>(true, both.server(), null), new Side<>(true, both.client(), null));
        } catch (AwaitTimeoutException e) {
            return new Outcome<>(false, e.elapsed(), side(e, "server"), side(e, "client"));
        }
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
