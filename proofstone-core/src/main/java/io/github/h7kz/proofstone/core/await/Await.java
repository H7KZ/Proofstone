package io.github.h7kz.proofstone.core.await;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * The only place in the framework that waits on wall-clock time (ADR-0007, AGENTS.md §3).
 *
 * <p>Waiting is predicate polling with exponential backoff, never a fixed sleep. A probe that throws is
 * recorded and polled again, because it usually means the state is not there yet. Nothing retries the
 * predicate, so a wrong answer still fails at the timeout.
 */
public final class Await {

    /** First pause after a probe that did not satisfy the predicate. */
    static final long FIRST_PAUSE_MILLIS = 25;

    /** Upper bound on the pause between two probes. */
    static final long MAX_PAUSE_MILLIS = 250;

    private static final long NANOS_PER_MILLI = 1_000_000L;

    private Await() {
    }

    /** Pauses the calling thread. Only {@link #pauseMillis} implements it, with a real sleep. */
    @FunctionalInterface
    interface Pause {
        void run(long millis) throws InterruptedException;
    }

    /** The values seen by both sides of a dual-oracle wait, as of the last probe. */
    public record Both<A, B>(A server, B client) {
    }

    /**
     * Polls {@code probe} until {@code ok} accepts its value.
     *
     * @throws AwaitTimeoutException when the timeout passes first; it carries the last value and exception
     */
    public static <T> T until(String description, Supplier<T> probe, Predicate<T> ok, Duration timeout) {
        return until(description, probe, ok, timeout, System::nanoTime, Await::pauseMillis);
    }

    /**
     * Polls both probes until {@code ok} accepts both values together. Used for the dual oracle (test-api §5).
     *
     * @throws AwaitTimeoutException when the timeout passes first; it records the last value of each side
     */
    public static <A, B> Both<A, B> untilBoth(String description, Supplier<A> server, Supplier<B> client,
            BiPredicate<A, B> ok, Duration timeout) {
        return untilBoth(description, server, client, ok, timeout, System::nanoTime, Await::pauseMillis);
    }

    static <T> T until(String description, Supplier<T> probe, Predicate<T> ok, Duration timeout,
            LongSupplier clock, Pause pause) {
        Watch<T> value = new Watch<>("value", probe);
        poll(description, () -> value.hasValue() && ok.test(value.value()), timeout, clock, pause, value);
        return value.value();
    }

    static <A, B> Both<A, B> untilBoth(String description, Supplier<A> server, Supplier<B> client,
            BiPredicate<A, B> ok, Duration timeout, LongSupplier clock, Pause pause) {
        Watch<A> serverWatch = new Watch<>("server", server);
        Watch<B> clientWatch = new Watch<>("client", client);
        poll(description, () -> serverWatch.hasValue() && clientWatch.hasValue()
                && ok.test(serverWatch.value(), clientWatch.value()), timeout, clock, pause,
                serverWatch, clientWatch);
        return new Both<>(serverWatch.value(), clientWatch.value());
    }

    private static void poll(String description, BooleanSupplier satisfied, Duration timeout,
            LongSupplier clock, Pause pause, Watch<?>... watches) {
        Objects.requireNonNull(description, "description");
        if (timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("timeout must be positive: " + timeout);
        }
        long limitNanos = timeout.toNanos();
        long start = clock.getAsLong();
        long pauseMillis = FIRST_PAUSE_MILLIS;
        while (true) {
            for (Watch<?> watch : watches) {
                watch.sample();
            }
            if (satisfied.getAsBoolean()) {
                return;
            }
            long elapsedNanos = clock.getAsLong() - start;
            if (elapsedNanos >= limitNanos) {
                List<AwaitTimeoutException.Observation> observations = new ArrayList<>();
                for (Watch<?> watch : watches) {
                    observations.add(watch.observation());
                }
                throw new AwaitTimeoutException(description, Duration.ofNanos(elapsedNanos), observations);
            }
            // Round up so the last pause never ends short of the deadline and leaves one probe too few.
            long remainingMillis = (limitNanos - elapsedNanos + NANOS_PER_MILLI - 1) / NANOS_PER_MILLI;
            try {
                pause.run(Math.min(pauseMillis, remainingMillis));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("await interrupted: " + description, e);
            }
            pauseMillis = Math.min(pauseMillis * 2, MAX_PAUSE_MILLIS);
        }
    }

    private static void pauseMillis(long millis) throws InterruptedException {
        Thread.sleep(millis);
    }

    /** Records the last value and the last exception of one probe. */
    private static final class Watch<T> {
        private final String label;
        private final Supplier<T> source;
        private T value;
        private boolean hasValue;
        private Throwable lastException;

        Watch(String label, Supplier<T> source) {
            this.label = label;
            this.source = source;
        }

        void sample() {
            try {
                value = source.get();
                hasValue = true;
                lastException = null;
            } catch (RuntimeException | AssertionError e) {
                // AssertionError: probes often wrap assertions, and a failed assertion is "not yet".
                lastException = e;
            }
        }

        T value() {
            return value;
        }

        boolean hasValue() {
            return hasValue;
        }

        AwaitTimeoutException.Observation observation() {
            return new AwaitTimeoutException.Observation(label, hasValue, value, lastException);
        }
    }
}
