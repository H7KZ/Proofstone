package io.github.h7kz.proofstone.core.host;

import io.github.h7kz.proofstone.core.driver.bedrock.rpc.BedrockRpc;

/**
 * How a Geyser cell's Bedrock clients reach Geyser (ADR-0021): {@code bedrock.transport} in Geyser's config and the transport the
 * Bedrock driver joins with. {@code both} is the fallback that keeps RakNet beside NetherNet until the NetherNet cell is green.
 */
public enum GeyserTransport {
    /** RakNet (UDP 19132) and NetherNet (signalling TCP 19132, media UDP 19133): the driver joins over RakNet. */
    BOTH("both", BedrockRpc.TRANSPORT_RAKNET, "Started Geyser on UDP port " + GeyserHost.BEDROCK_PORT),
    /** NetherNet only: the driver joins over NetherNet. */
    NETHERNET("nethernet", BedrockRpc.TRANSPORT_NETHERNET, "Built-in signaling started on http://0.0.0.0:" + GeyserHost.BEDROCK_PORT);

    private final String geyserValue;
    private final String driverTransport;
    private final String readyLine;

    GeyserTransport(String geyserValue, String driverTransport, String readyLine) {
        this.geyserValue = geyserValue;
        this.driverTransport = driverTransport;
        this.readyLine = readyLine;
    }

    /** The value of {@code bedrock.transport}. */
    public String geyserValue() {
        return geyserValue;
    }

    /** The transport word of the driver RPC ({@code BedrockRpc.TRANSPORT_*}). */
    public String driverTransport() {
        return driverTransport;
    }

    /** The server log line that shows Geyser accepts Bedrock clients on this transport. */
    public String readyLine() {
        return readyLine;
    }

    /** The value of {@code proofstone.geyser.transport} ({@code both} or {@code nethernet}); absent means {@link #BOTH}; any other word is an error. */
    public static GeyserTransport fromSystemProperty() {
        String value = System.getProperty("proofstone.geyser.transport");
        if (value == null) {
            return BOTH;
        }
        for (GeyserTransport transport : values()) {
            if (transport.geyserValue.equals(value)) {
                return transport;
            }
        }
        throw new PaperHostException("proofstone.geyser.transport '" + value + "' is not both or nethernet");
    }
}
