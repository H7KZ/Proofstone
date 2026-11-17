package io.github.h7kz.proofstone.driver.bedrock;

/**
 * The protocol number the driver sends in {@code RequestNetworkSettings}. Always the codec's own, except for the negative control
 * of T-M10-05: a Bedrock protocol the Geyser build refuses cannot be made by any declared input, so the maintainer approved one
 * default-off, test-only switch. It applies only when the fault injection ({@code -Dproofstone.injectGeyserFault=refused-protocol},
 * set by the harness through the container's {@code JAVA_TOOL_OPTIONS}) is set too; the number alone is an error, never ignored.
 */
final class ProtocolOverride {

    static final String PROPERTY = "proofstone.bedrock.protocolOverride";
    static final String FAULT_PROPERTY = "proofstone.injectGeyserFault";
    static final String FAULT_VALUE = "refused-protocol";

    private ProtocolOverride() {
    }

    /** The protocol to send, from the two property values (null when unset). */
    static int resolve(String override, String fault, int codecProtocol) {
        if (override == null || override.isBlank()) {
            return codecProtocol;
        }
        if (!FAULT_VALUE.equals(fault)) {
            throw new IllegalStateException("-D" + PROPERTY + " is a test-only switch, valid only with -D" + FAULT_PROPERTY + "="
                    + FAULT_VALUE + " (T-M10-05); got fault \"" + fault + "\"");
        }
        int number;
        try {
            number = Integer.parseInt(override.trim());
        } catch (NumberFormatException e) {
            throw new IllegalStateException("-D" + PROPERTY + " must be a positive integer, not \"" + override + "\"", e);
        }
        if (number <= 0) {
            throw new IllegalStateException("-D" + PROPERTY + " must be a positive integer, not \"" + override + "\"");
        }
        if (number == codecProtocol) {
            throw new IllegalStateException("-D" + PROPERTY + "=" + number + " is the codec's own protocol, not a refusal");
        }
        return number;
    }

    static int fromSystemProperties(int codecProtocol) {
        return resolve(System.getProperty(PROPERTY), System.getProperty(FAULT_PROPERTY), codecProtocol);
    }
}
