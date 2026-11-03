package dev.proofstone.agent.server;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.proofstone.agent.protocol.Health;
import java.io.File;
import java.io.IOException;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Replays the golden cases (proofstone-protocol/golden) against a live {@link AgentServer} over HTTP. The
 * game state is a {@link FakeGame}, so this checks the wire contract, not Bukkit. The real-server replay is
 * T-M1-05. The golden directory comes from the {@code proofstone.golden} system property (build.gradle.kts).
 */
class GoldenContractTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String TOKEN = "s3cret";
    private static final File GOLDEN = new File(System.getProperty("proofstone.golden"));

    private FakeGame game;
    private AgentServer server;
    private AgentHttp http;

    @BeforeEach
    void start() throws IOException {
        Health health = new Health("0.1.0", "1.0", "paper", "1.8.8", FakeGame.capabilities());
        game = new FakeGame();
        server = new AgentServer(new AgentConfig(TOKEN, 0), health, game);
        server.start();
        http = new AgentHttp(server.port());
    }

    @AfterEach
    void stop() {
        server.stop();
    }

    static Stream<String> cases() {
        return Stream.of(
                "health/ok",
                "health/unauthorised",
                "command/reply",
                "players/not-found",
                "players/empty-inventory");
    }

    @ParameterizedTest
    @MethodSource("cases")
    void replaysTheGoldenCase(String name) throws IOException {
        JsonNode request = read(name + ".request.json");
        JsonNode expected = read(name + ".response.json");
        String authorization = request.has("auth") && !request.get("auth").asBoolean()
                ? null
                : "Bearer " + TOKEN;
        String body = request.has("body") ? MAPPER.writeValueAsString(request.get("body")) : null;

        AgentHttp.Response actual = http.send(
                request.get("method").asText(), request.get("path").asText(), authorization, body);

        assertThat(actual.status).isEqualTo(expected.get("status").asInt());
        GoldenShape.assertMatches(expected.get("body"), MAPPER.readTree(actual.body));
    }

    private static JsonNode read(String relative) throws IOException {
        return MAPPER.readTree(new File(GOLDEN, relative));
    }
}
