package io.github.h7kz.proofstone.core.host;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * A Geyser cell misconfiguration injected on purpose (T-M9-11), switched on by {@code -Dproofstone.injectGeyserFault}, the way
 * {@link TopologyFault} breaks a topology. Each fault must turn the cell's first check red, with a message that names the hop; a
 * fault that passes is a failure of {@code metaBedrockTest}. Test-only: nothing in a user suite sets the property.
 *
 * <p>Only the faults shown reachable with a real run are here (geyser.md, "Negative controls"). A refused Bedrock protocol is not:
 * the driver pins one codec and sends it, so the harness cannot make the pinned Geyser build refuse it without a switch in the
 * driver (a production path, outside T-M9-11), and Geyser has no setting that narrows its range.
 */
public enum GeyserFault {
    /** No fault: the clean cell. */
    NONE("false"),
    /** The stand-in refuses the two Microsoft discovery names (as if absent), so the server and the client JVM cannot fetch them. */
    STAND_IN_ABSENT("stand-in-absent"),
    /** Geyser reads a Floodgate key that is not the one the Floodgate plugin holds. */
    KEY_MISMATCH("key-mismatch");

    /** The system property that selects a fault. The Gradle build forwards it to the test JVM. */
    public static final String PROPERTY = "proofstone.injectGeyserFault";

    private final String value;

    GeyserFault(String value) {
        this.value = value;
    }

    /** The property value that selects this fault. */
    public String value() {
        return value;
    }

    /** {@code null}, empty and {@code false} mean no fault; anything not listed is an error, never a silent clean run. */
    public static GeyserFault parse(String value) {
        if (value == null || value.isBlank()) {
            return NONE;
        }
        for (GeyserFault fault : values()) {
            if (fault.value.equals(value)) {
                return fault;
            }
        }
        throw new IllegalArgumentException("-D" + PROPERTY + " must be one of "
                + Arrays.stream(values()).map(GeyserFault::value).collect(Collectors.joining(", ")) + ", not \"" + value + "\"");
    }

    /** The fault the current run asks for. */
    public static GeyserFault fromSystemProperty() {
        return parse(System.getProperty(PROPERTY));
    }
}
