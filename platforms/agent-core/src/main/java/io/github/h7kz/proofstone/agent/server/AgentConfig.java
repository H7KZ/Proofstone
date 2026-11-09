package io.github.h7kz.proofstone.agent.server;

import java.util.Map;

/** Agent settings from environment variables (spec §1). The token is mandatory: no token, no HTTP server. */
public final class AgentConfig {

    static final int DEFAULT_PORT = 25580;

    private final String token;
    private final int port;

    public AgentConfig(String token, int port) {
        this.token = token;
        this.port = port;
    }

    public static AgentConfig fromEnvironment(Map<String, String> env) {
        String token = env.get("PROOFSTONE_AGENT_TOKEN");
        if (token == null || token.isEmpty()) {
            throw new IllegalStateException("PROOFSTONE_AGENT_TOKEN is not set");
        }
        // A malformed port is a startup error, never a silent default.
        int port = Integer.parseInt(env.getOrDefault("PROOFSTONE_AGENT_PORT", String.valueOf(DEFAULT_PORT)));
        return new AgentConfig(token, port);
    }

    public String token() {
        return token;
    }

    public int port() {
        return port;
    }
}
