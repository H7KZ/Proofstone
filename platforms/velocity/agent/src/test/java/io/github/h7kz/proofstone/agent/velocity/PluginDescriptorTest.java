package io.github.h7kz.proofstone.agent.velocity;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.velocitypowered.api.proxy.ProxyServer;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

/** Velocity finds the plugin through {@code velocity-plugin.json}; a wrong entry here is a plugin that silently never loads. */
class PluginDescriptorTest {

    private static JsonNode descriptor() throws IOException {
        try (InputStream in = PluginDescriptorTest.class.getResourceAsStream("/velocity-plugin.json")) {
            assertThat(in).as("velocity-plugin.json on the classpath").isNotNull();
            return new ObjectMapper().readTree(in);
        }
    }

    @Test
    void theMainClassIsThePluginAndItsVersionMatches() throws Exception {
        JsonNode json = descriptor();
        assertThat(Class.forName(json.get("main").asText())).isEqualTo(ProofstoneAgentPlugin.class);
        assertThat(json.get("version").asText()).isEqualTo(ProofstoneAgentPlugin.VERSION);
    }

    @Test
    void theIdIsOneVelocityAccepts() throws IOException {
        assertThat(descriptor().get("id").asText()).matches("[a-z][a-z0-9_-]{0,63}");
    }

    @Test
    void theConstructorIsInjectedWithTheProxyAndALogger() throws NoSuchMethodException {
        Constructor<ProofstoneAgentPlugin> constructor =
                ProofstoneAgentPlugin.class.getConstructor(ProxyServer.class, Logger.class);
        assertThat(constructor.isAnnotationPresent(Inject.class)).isTrue();
    }
}
