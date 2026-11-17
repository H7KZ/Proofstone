package io.github.h7kz.proofstone.core.host;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * A Geyser cell misconfiguration injected on purpose (T-M9-11), switched on by {@code -Dproofstone.injectGeyserFault}, the way
 * {@link TopologyFault} breaks a topology. Each fault must turn the cell's first check red, with a message that names the hop; a
 * fault that passes is a failure of {@code metaBedrockTest}. Test-only: nothing in a user suite sets the property.
 *
 * <p>Only the faults shown reachable with a real run are here (geyser.md, "Negative controls"). A refused Bedrock protocol needed
 * a switch in the driver (Geyser has no setting that narrows its range): {@code -Dproofstone.bedrock.protocolOverride}, default off,
 * read by the driver host's {@code HostSession}, set only by {@link #driverHostOptions()} (T-M10-05).
 */
public enum GeyserFault {
    /** No fault: the clean cell. */
    NONE("false"),
    /** The stand-in refuses the two Microsoft discovery names (as if absent), so the server and the client JVM cannot fetch them. */
    STAND_IN_ABSENT("stand-in-absent"),
    /** Geyser reads a Floodgate key that is not the one the Floodgate plugin holds. */
    KEY_MISMATCH("key-mismatch"),
    /** The driver host sends a Bedrock protocol number the pinned Geyser build refuses (T-M10-05, the one driver switch). */
    REFUSED_PROTOCOL("refused-protocol");

    /** The protocol number the driver host sends under {@link #REFUSED_PROTOCOL}; recorded with Geyser's refusal in geyser.md. */
    public static final int REFUSED_PROTOCOL_NUMBER = 9999;

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

    /**
     * The extra JVM options of the driver host's {@code JAVA_TOOL_OPTIONS}: empty for every fault but {@link #REFUSED_PROTOCOL}, so
     * the switch is set by nothing else. The driver host refuses the number without the fault property beside it.
     */
    String driverHostOptions() {
        return this == REFUSED_PROTOCOL
                ? " -D" + PROPERTY + "=" + value + " -Dproofstone.bedrock.protocolOverride=" + REFUSED_PROTOCOL_NUMBER
                : "";
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
