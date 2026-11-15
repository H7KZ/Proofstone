package io.github.h7kz.proofstone.agent.server;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.h7kz.proofstone.agent.protocol.Health;
import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * {@code GET /v1/datapacks} (protocol 1.3, ADR-0017 section 9): a {@link io.github.h7kz.proofstone.agent.game.Game}
 * that lists packs overrides {@code dataPacks}; every other agent leaves the default, a 404, so one without the token
 * never serves it.
 */
class DataPackRoutesTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String TOKEN = "s3cret";

    @Test
    void anAgentWithoutTheTokenAnswersNotFound() throws IOException {
        AgentServer server = start(new FakeGame());
        try {
            AgentHttp.Response reply = new AgentHttp(server.port()).send("GET", "/v1/datapacks", "Bearer " + TOKEN, null);
            assertThat(reply.status).isEqualTo(404);
            assertThat(MAPPER.readTree(reply.body).get("error").get("code").asText()).isEqualTo("NOT_FOUND");
        } finally {
            server.stop();
        }
    }

    @Test
    void anAgentThatListsPacksAnswersEnabledAndAvailableInTheEnvelope() throws IOException {
        AgentServer server = start(new FakeGame() {
            @Override
            public Map<String, Object> dataPacks(long timeoutMs) {
                Map<String, Object> out = new LinkedHashMap<>();
                out.put("enabled", Arrays.asList("vanilla", "file/x.zip"));
                out.put("available", Arrays.asList("vanilla", "file/x.zip", "file/y.zip"));
                return out;
            }
        });
        try {
            AgentHttp http = new AgentHttp(server.port());
            JsonNode reply = MAPPER.readTree(http.send("GET", "/v1/datapacks", "Bearer " + TOKEN, null).body);
            assertThat(reply.get("ok").asBoolean()).isTrue();
            assertThat(reply.get("data").get("enabled").get(1).asText()).isEqualTo("file/x.zip");
            assertThat(reply.get("data").get("available").size()).isEqualTo(3);
            assertThat(http.send("POST", "/v1/datapacks", "Bearer " + TOKEN, "{}").status).isEqualTo(405);
        } finally {
            server.stop();
        }
    }

    private static AgentServer start(FakeGame game) throws IOException {
        AgentServer server = new AgentServer(new AgentConfig(TOKEN, 0),
                new Health("0.1.0", "1.3", "fabric", "26.3", FakeGame.capabilities()), game);
        server.start();
        return server;
    }
}
