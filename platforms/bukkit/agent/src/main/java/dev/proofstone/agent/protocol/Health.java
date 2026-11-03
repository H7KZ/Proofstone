package dev.proofstone.agent.protocol;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** Reply of {@code GET /v1/health}. Java 8 twin of the Java 17 record in proofstone-protocol. */
public final class Health {

    private final String agentVersion;
    private final String protocol;
    private final String platform;
    private final String mcVersion;
    private final List<String> capabilities;

    @JsonCreator
    public Health(
            @JsonProperty("agentVersion") String agentVersion,
            @JsonProperty("protocol") String protocol,
            @JsonProperty("platform") String platform,
            @JsonProperty("mcVersion") String mcVersion,
            @JsonProperty("capabilities") List<String> capabilities) {
        this.agentVersion = agentVersion;
        this.protocol = protocol;
        this.platform = platform;
        this.mcVersion = mcVersion;
        this.capabilities = capabilities;
    }

    public String getAgentVersion() {
        return agentVersion;
    }

    public String getProtocol() {
        return protocol;
    }

    public String getPlatform() {
        return platform;
    }

    public String getMcVersion() {
        return mcVersion;
    }

    public List<String> getCapabilities() {
        return capabilities;
    }
}
