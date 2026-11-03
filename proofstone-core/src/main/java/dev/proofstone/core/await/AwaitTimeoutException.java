package dev.proofstone.core.await;

import java.time.Duration;
import java.util.List;

/**
 * Thrown when {@link Await} gives up. The message names the wait, so a failure report says what it was
 * waiting for, how long it waited, and what each side last showed (ADR-0007).
 */
public final class AwaitTimeoutException extends RuntimeException {

    private final String description;
    private final Duration elapsed;
    private final List<Observation> observations;

    AwaitTimeoutException(String description, Duration elapsed, List<Observation> observations) {
        super(message(description, elapsed, observations));
        this.description = description;
        this.elapsed = elapsed;
        this.observations = List.copyOf(observations);
    }

    public String description() {
        return description;
    }

    public Duration elapsed() {
        return elapsed;
    }

    /** One entry per probe: {@code value} for {@code until}, {@code server} and {@code client} for {@code untilBoth}. */
    public List<Observation> observations() {
        return observations;
    }

    private static String message(String description, Duration elapsed, List<Observation> observations) {
        StringBuilder message = new StringBuilder("await timed out after ")
                .append(elapsed.toMillis()).append(" ms: ").append(description);
        for (Observation observation : observations) {
            message.append("\n  ").append(observation.describe());
        }
        return message.toString();
    }

    /** The last state of one probe. {@code lastValue} is only meaningful when {@code hasValue} is true. */
    public record Observation(String label, boolean hasValue, Object lastValue, Throwable lastException) {

        String describe() {
            return label + ": last value " + (hasValue ? lastValue : "none")
                    + "; last exception " + (lastException == null ? "none" : lastException);
        }
    }
}
