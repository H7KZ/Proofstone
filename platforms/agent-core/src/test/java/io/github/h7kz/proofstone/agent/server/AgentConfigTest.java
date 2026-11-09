package io.github.h7kz.proofstone.agent.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AgentConfigTest {

    @Test
    void missingTokenIsAnError() {
        assertThatThrownBy(() -> AgentConfig.fromEnvironment(Collections.<String, String>emptyMap()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PROOFSTONE_AGENT_TOKEN");
    }

    @Test
    void emptyTokenIsAnError() {
        assertThatThrownBy(() -> AgentConfig.fromEnvironment(env("PROOFSTONE_AGENT_TOKEN", "")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void portDefaultsTo25580() {
        AgentConfig config = AgentConfig.fromEnvironment(env("PROOFSTONE_AGENT_TOKEN", "t"));

        assertThat(config.port()).isEqualTo(25580);
    }

    @Test
    void malformedPortFailsLoudly() {
        Map<String, String> env = env("PROOFSTONE_AGENT_TOKEN", "t");
        env.put("PROOFSTONE_AGENT_PORT", "not-a-port");

        assertThatThrownBy(() -> AgentConfig.fromEnvironment(env))
                .isInstanceOf(NumberFormatException.class);
    }

    private static Map<String, String> env(String key, String value) {
        Map<String, String> env = new HashMap<>();
        env.put(key, value);
        return env;
    }
}
