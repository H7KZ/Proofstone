package dev.proofstone.agent.protocol;

/**
 * Inline examples for wire protocol v1. Used until the golden files from T-M1-02 land. The round-trip
 * test drops JSON nulls, so examples carry none. Concatenated strings: tests compile with --release 8.
 */
final class ProtocolExamples {

    static final String HEALTH = "{\"agentVersion\":\"0.1.0\",\"protocol\":\"1.0\",\"platform\":\"paper\","
            + "\"mcVersion\":\"1.8.8\",\"capabilities\":[\"command\"]}";

    static final String ERROR = "{\"code\":\"UNKNOWN_KEY\",\"message\":\"unknown material\","
            + "\"detail\":{\"key\":\"minecraft:foo\"}}";

    static final String ENVELOPE_OK = "{\"ok\":true,\"data\":{\"agentVersion\":\"0.1.0\",\"protocol\":\"1.0\","
            + "\"platform\":\"paper\",\"mcVersion\":\"1.8.8\",\"capabilities\":[]}}";

    static final String ENVELOPE_ERROR = "{\"ok\":false,\"error\":{\"code\":\"TIMEOUT\",\"message\":\"timed out\"}}";

    private ProtocolExamples() {
    }
}
