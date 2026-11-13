package io.github.h7kz.proofstone.core.host;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * A topology misconfiguration injected on purpose (T-M7-13), switched on by {@code -Dproofstone.injectTopologyFault}, the
 * way {@code injectConformanceFault} breaks a single cell. Each fault must turn the topology conformance red with a
 * message that names its container and hop; a fault that passes is a failure of the meta test. Test-only: nothing in a
 * user suite sets the property.
 */
public enum TopologyFault {
    /** No fault: the clean topology. */
    NONE("false"),
    /** Velocity only: the first backend's forwarding secret differs from the proxy's. */
    WRONG_SECRET("wrong-secret"),
    /** The proxy forwards nothing (Velocity mode none, BungeeCord ip_forward false) while the backends expect it. */
    FORWARDING_OFF("forwarding-off"),
    /** The first backend does not accept forwarding (Velocity disabled, BungeeCord off) while the proxy sends it. */
    BACKEND_FORWARDING_OFF("backend-forwarding-off"),
    /** The proxy's address for the last backend resolves to nothing, so the proxy never reaches it. */
    UNREACHABLE_BACKEND("unreachable-backend");

    /** The system property that selects a fault. The Gradle build forwards it to the test JVM. */
    public static final String PROPERTY = "proofstone.injectTopologyFault";

    private final String value;

    TopologyFault(String value) {
        this.value = value;
    }

    /** The property value that selects this fault. */
    public String value() {
        return value;
    }

    /** {@code null}, empty and {@code false} mean no fault; anything not listed is an error, never a silent clean run. */
    public static TopologyFault parse(String value) {
        if (value == null || value.isBlank()) {
            return NONE;
        }
        for (TopologyFault fault : values()) {
            if (fault.value.equals(value)) {
                return fault;
            }
        }
        throw new IllegalArgumentException("-D" + PROPERTY + " must be one of "
                + Arrays.stream(values()).map(TopologyFault::value).collect(Collectors.joining(", ")) + ", not \"" + value
                + "\"");
    }

    /** The fault the current run asks for. */
    public static TopologyFault fromSystemProperty() {
        return parse(System.getProperty(PROPERTY));
    }

    /** A fault that cannot be applied to this proxy is an error: a run that silently injected nothing would pass. */
    public void requireApplicable(ProxyKind kind) {
        if (this == WRONG_SECRET && kind != ProxyKind.VELOCITY) {
            throw new IllegalArgumentException("fault " + value + " cannot be injected on " + kind.id() + ": "
                    + kind.id() + " has no forwarding secret (" + kind.forwarding() + " forwarding); use "
                    + FORWARDING_OFF.value + " or " + BACKEND_FORWARDING_OFF.value);
        }
    }
}
