package io.github.h7kz.proofstone.core.await;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class AwaitTest {

    /** Pausing advances the clock, so no test waits on the wall clock. */
    private static final class FakeClock {
        private long nanos;
        private final List<Long> pauses = new ArrayList<>();

        long nanos() {
            return nanos;
        }

        void pause(long millis) {
            pauses.add(millis);
            nanos += millis * 1_000_000L;
        }
    }

    @Test
    void returnsTheFirstValueThatSatisfiesThePredicate() {
        FakeClock clock = new FakeClock();
        AtomicInteger calls = new AtomicInteger();

        int result = Await.until("counter reaches 3", calls::incrementAndGet, v -> v >= 3,
                Duration.ofSeconds(1), clock::nanos, clock::pause);

        assertThat(result).isEqualTo(3);
        assertThat(clock.pauses).containsExactly(25L, 50L);
    }

    @Test
    void backsOffExponentiallyFromTwentyFiveMillisAndCapsAtTwoHundredFifty() {
        FakeClock clock = new FakeClock();

        assertThatThrownBy(() -> Await.until("never true", () -> 0, v -> false,
                Duration.ofSeconds(2), clock::nanos, clock::pause))
                .isInstanceOf(AwaitTimeoutException.class);

        assertThat(clock.pauses).containsExactly(25L, 50L, 100L, 200L, 250L, 250L, 250L, 250L, 250L, 250L, 125L);
    }

    @Test
    void recordsProbeExceptionsAndAssertionErrorsAndKeepsPolling() {
        FakeClock clock = new FakeClock();
        AtomicInteger calls = new AtomicInteger();

        String result = Await.until("window open", () -> {
            switch (calls.incrementAndGet()) {
                case 1:
                    throw new IllegalStateException("no window yet");
                case 2:
                    throw new AssertionError("not visible");
                default:
                    return "open";
            }
        }, "open"::equals, Duration.ofSeconds(1), clock::nanos, clock::pause);

        assertThat(result).isEqualTo("open");
        assertThat(calls.get()).isEqualTo(3);
    }

    @Test
    void timeoutCarriesDescriptionElapsedTimeLastValueAndLastException() {
        FakeClock clock = new FakeClock();
        AtomicInteger calls = new AtomicInteger();

        assertThatThrownBy(() -> Await.until("inventory open", () -> {
            int n = calls.incrementAndGet();
            if (n % 2 == 0) {
                throw new IllegalStateException("stale window " + n);
            }
            return n;
        }, v -> false, Duration.ofMillis(100), clock::nanos, clock::pause))
                .isInstanceOfSatisfying(AwaitTimeoutException.class, e -> {
                    assertThat(e.description()).isEqualTo("inventory open");
                    assertThat(e.elapsed()).isEqualTo(Duration.ofMillis(100));
                    assertThat(e.observations()).hasSize(1);
                    AwaitTimeoutException.Observation observation = e.observations().get(0);
                    assertThat(observation.label()).isEqualTo("value");
                    assertThat(observation.hasValue()).isTrue();
                    assertThat(observation.lastValue()).isEqualTo(3);
                    assertThat(observation.lastException())
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessage("stale window 4");
                });
    }

    @Test
    void timeoutMessageFormatIsPinned() {
        FakeClock clock = new FakeClock();
        AtomicInteger calls = new AtomicInteger();

        assertThatThrownBy(() -> Await.until("inventory open", () -> {
            int n = calls.incrementAndGet();
            if (n % 2 == 0) {
                throw new IllegalStateException("stale window " + n);
            }
            return n;
        }, v -> false, Duration.ofMillis(100), clock::nanos, clock::pause))
                .hasMessage("await timed out after 100 ms: inventory open\n"
                        + "  value: last value 3; last exception java.lang.IllegalStateException: stale window 4");
    }

    @Test
    void untilBothWaitsForBothSidesAndReturnsBothValues() {
        FakeClock clock = new FakeClock();
        AtomicInteger server = new AtomicInteger();

        Await.Both<Integer, Integer> result = Await.untilBoth("levels agree", server::incrementAndGet, () -> 3,
                Integer::equals, Duration.ofSeconds(1), clock::nanos, clock::pause);

        assertThat(result).isEqualTo(new Await.Both<>(3, 3));
        assertThat(clock.pauses).containsExactly(25L, 50L);
    }

    @Test
    void untilBothTimeoutRecordsLastValueOfEachSide() {
        FakeClock clock = new FakeClock();

        assertThatThrownBy(() -> Await.untilBoth("levels agree", () -> 1, () -> {
            throw new IllegalStateException("no client");
        }, (s, c) -> false, Duration.ofMillis(50), clock::nanos, clock::pause))
                .isInstanceOfSatisfying(AwaitTimeoutException.class, e -> {
                    assertThat(e.getMessage()).isEqualTo("await timed out after 50 ms: levels agree\n"
                            + "  server: last value 1; last exception none\n"
                            + "  client: last value none; last exception java.lang.IllegalStateException: no client");
                });
    }

    @Test
    void interruptionFailsLoudlyAndRestoresTheInterruptFlag() {
        assertThatThrownBy(() -> Await.until("never", () -> 0, v -> false, Duration.ofSeconds(1),
                () -> 0L, millis -> {
                    throw new InterruptedException();
                }))
                .isInstanceOf(IllegalStateException.class)
                .hasCauseInstanceOf(InterruptedException.class);
        assertThat(Thread.interrupted()).isTrue();
    }

    @Test
    void rejectsNonPositiveTimeout() {
        assertThatThrownBy(() -> Await.until("zero", () -> 0, v -> true, Duration.ZERO,
                () -> 0L, millis -> { }))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void succeedsWithoutPausingWhenTheFirstProbePasses() {
        FakeClock clock = new FakeClock();

        assertThat(Await.until("immediate", () -> "ready", "ready"::equals,
                Duration.ofSeconds(10), clock::nanos, clock::pause)).isEqualTo("ready");
        assertThat(clock.pauses).isEmpty();
    }
}
