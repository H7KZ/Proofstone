package io.github.h7kz.proofstone.agent.server;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.h7kz.proofstone.agent.protocol.Health;
import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * {@code GET /v1/mods} (protocol 1.3, ADR-0017 section 9): a mod agent's {@link io.github.h7kz.proofstone.agent.game.Game}
 * overrides {@code mods}, every other agent leaves the default, which is a 404, so a plugin agent never serves it.
 */
class ModRoutesTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String TOKEN = "s3cret";

    @Test
    void anAgentWithoutModsAnswersNotFound() throws IOException {
        AgentServer server = start(new FakeGame());
        try {
            AgentHttp.Response reply = new AgentHttp(server.port()).send("GET", "/v1/mods", "Bearer " + TOKEN, null);
            assertThat(reply.status).isEqualTo(404);
            assertThat(MAPPER.readTree(reply.body).get("error").get("code").asText()).isEqualTo("NOT_FOUND");
        } finally {
            server.stop();
        }
    }

    @Test
    void aModAgentListsItsModsInTheEnvelope() throws IOException {
        AgentServer server = start(new FakeGame() {
            @Override
            public List<Map<String, Object>> mods(long timeoutMs) {
                return Arrays.asList(mod("fabric-api", "0.166.2+26.3"), mod("proofstone-agent", "0.1.0"));
            }
        });
        try {
            AgentHttp http = new AgentHttp(server.port());
            JsonNode reply = MAPPER.readTree(http.send("GET", "/v1/mods", "Bearer " + TOKEN, null).body);
            assertThat(reply.get("ok").asBoolean()).isTrue();
            assertThat(reply.get("data").get("mods").get(0).get("id").asText()).isEqualTo("fabric-api");
            assertThat(reply.get("data").get("mods").get(1).get("version").asText()).isEqualTo("0.1.0");
            assertThat(http.send("POST", "/v1/mods", "Bearer " + TOKEN, "{}").status).isEqualTo(405);
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

    private static Map<String, Object> mod(String id, String version) {
        Map<String, Object> mod = new LinkedHashMap<>();
        mod.put("id", id);
        mod.put("version", version);
        return mod;
    }
}
