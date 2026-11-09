package io.github.h7kz.proofstone.agent.server;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.h7kz.proofstone.agent.protocol.Health;
import java.io.IOException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** A backend agent never serves {@code /v1/proxy/*} (spec §2): a {@link FakeGame} leaves the proxy reads at their default. */
class ProxyRoutesTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String TOKEN = "s3cret";

    private AgentServer server;
    private AgentHttp http;

    @BeforeEach
    void start() throws IOException {
        server = new AgentServer(new AgentConfig(TOKEN, 0),
                new Health("0.1.0", "1.2", "paper", "1.8.8", FakeGame.capabilities()), new FakeGame());
        server.start();
        http = new AgentHttp(server.port());
    }

    @AfterEach
    void stop() {
        server.stop();
    }

    @Test
    void aBackendAgentAnswersNotFoundForTheServerList() throws IOException {
        assertNotFound("/v1/proxy/servers");
    }

    @Test
    void aBackendAgentAnswersNotFoundForOneServer() throws IOException {
        assertNotFound("/v1/proxy/servers/lobby");
    }

    @Test
    void theProxyRoutesAreReadOnly() throws IOException {
        AgentHttp.Response reply = http.send("POST", "/v1/proxy/servers", "Bearer " + TOKEN, "{}");
        assertThat(reply.status).isEqualTo(405);
        assertThat(MAPPER.readTree(reply.body).get("error").get("code").asText()).isEqualTo("METHOD_NOT_ALLOWED");
    }

    private void assertNotFound(String path) throws IOException {
        AgentHttp.Response reply = http.send("GET", path, "Bearer " + TOKEN, null);
        assertThat(reply.status).isEqualTo(404);
        JsonNode error = MAPPER.readTree(reply.body).get("error");
        assertThat(error.get("code").asText()).isEqualTo("NOT_FOUND");
    }
}
