package dev.proofstone.agent.server;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.proofstone.agent.protocol.Health;
import java.io.IOException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Real HTTP round trip on an ephemeral port, against a {@link FakeGame}. */
class AgentServerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String TOKEN = "s3cret";
    private static final String BEARER = "Bearer " + TOKEN;

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

    @Test
    void everyEndpointRequiresTheToken() throws IOException {
        assertThat(http.send("GET", "/v1/health", null, null).status).isEqualTo(401);
        assertThat(http.send("GET", "/v1/players", "Bearer nope", null).status).isEqualTo(401);
        assertThat(http.send("POST", "/v1/command", null, "{\"command\":\"list\"}").status).isEqualTo(401);
    }

    @Test
    void commandReturnsTheRawOutputAndPassesTheTimeoutOn() throws IOException {
        AgentHttp.Response response = http.send("POST", "/v1/command", BEARER,
                "{\"command\":\"version\",\"timeoutMs\":2500}");

        assertThat(response.status).isEqualTo(200);
        assertThat(game.lastCommand).isEqualTo("version");
        assertThat(game.lastTimeoutMs).isEqualTo(2500);
        assertThat(MAPPER.readTree(response.body).at("/data/raw").asText()).isEqualTo("Server version 1.8.8");
    }

    @Test
    void commandDefaultsTheTimeoutToTenSeconds() throws IOException {
        http.send("POST", "/v1/command", BEARER, "{\"command\":\"version\"}");

        assertThat(game.lastTimeoutMs).isEqualTo(10_000);
    }

    @Test
    void aCommandBodyWithoutACommandIsAnInvalidRequest() throws IOException {
        AgentHttp.Response response = http.send("POST", "/v1/command", BEARER, "{}");

        assertThat(response.status).isEqualTo(400);
        assertThat(MAPPER.readTree(response.body).at("/error/code").asText()).isEqualTo("INVALID_REQUEST");
    }

    @Test
    void aNonJsonBodyIsAnInvalidRequest() throws IOException {
        AgentHttp.Response response = http.send("POST", "/v1/command", BEARER, "not json");

        assertThat(response.status).isEqualTo(400);
        assertThat(MAPPER.readTree(response.body).at("/error/code").asText()).isEqualTo("INVALID_REQUEST");
    }

    @Test
    void playersListsTheOnlineNames() throws IOException {
        AgentHttp.Response response = http.send("GET", "/v1/players", BEARER, null);

        assertThat(response.status).isEqualTo(200);
        assertThat(MAPPER.readTree(response.body).at("/data/players/0").asText()).isEqualTo("Alex");
    }

    @Test
    void aMissingPlayerIsPlayerNotFoundWithTheNameAsDetail() throws IOException {
        AgentHttp.Response response = http.send("GET", "/v1/players/Nobody", BEARER, null);

        assertThat(response.status).isEqualTo(404);
        assertThat(MAPPER.readTree(response.body).at("/error/code").asText()).isEqualTo("PLAYER_NOT_FOUND");
        assertThat(MAPPER.readTree(response.body).at("/error/detail/name").asText()).isEqualTo("Nobody");
    }

    @Test
    void unknownPathsAreNotFoundAndWrongMethodsAreRejected() throws IOException {
        assertThat(http.send("GET", "/v1/nothing", BEARER, null).status).isEqualTo(404);
        assertThat(http.send("GET", "/v1/command", BEARER, null).status).isEqualTo(405);
        assertThat(http.send("POST", "/v1/health", BEARER, "{}").status).isEqualTo(405);
    }

    @Test
    void healthWithTheTokenIsTheEnvelopeWithItsCapabilities() throws IOException {
        AgentHttp.Response response = http.send("GET", "/v1/health", BEARER, null);

        assertThat(response.status).isEqualTo(200);
        assertThat(MAPPER.readTree(response.body).at("/data/capabilities").size()).isEqualTo(2);
    }

    @Test
    void unauthorisedBodyUsesTheSpecErrorCode() throws IOException {
        AgentHttp.Response response = http.send("GET", "/v1/health", null, null);

        // Code per agent-protocol.md §2 and golden/health/unauthorised (American spelling).
        assertThat(MAPPER.readTree(response.body).at("/error/code").asText()).isEqualTo("UNAUTHORIZED");
        assertThat(MAPPER.readTree(response.body).at("/ok").asBoolean()).isFalse();
    }

    @Test
    void giveResetAndPatchReachTheGameWithTheirTimeout() throws IOException {
        AgentHttp.Response give = http.send("POST", "/v1/players/Alex/give", BEARER,
                "{\"type\":\"minecraft:diamond_sword\",\"amount\":1,\"timeoutMs\":3000}");

        assertThat(give.status).isEqualTo(200);
        assertThat(MAPPER.readTree(give.body).at("/data").size()).isZero();
        assertThat(game.lastTimeoutMs).isEqualTo(3000);
        assertThat(http.send("POST", "/v1/players/Alex/reset", BEARER, null).status).isEqualTo(200);
        assertThat(game.lastReset).isEqualTo("Alex");
        assertThat(http.send("PATCH", "/v1/players/Alex", BEARER, "{\"level\":5}").status).isEqualTo(200);
        assertThat(game.lastPatch).containsEntry("level", 5);
    }

    @Test
    void anUnknownKeyIsAnErrorAndLeavesNoItemBehind() throws IOException {
        AgentHttp.Response give = http.send("POST", "/v1/players/Alex/give", BEARER,
                "{\"type\":\"minecraft:not_a_thing\",\"amount\":1}");

        assertThat(give.status).isEqualTo(400);
        assertThat(MAPPER.readTree(give.body).at("/error/code").asText()).isEqualTo("UNKNOWN_KEY");
        AgentHttp.Response read = http.send("GET", "/v1/players/Alex", BEARER, null);
        assertThat(MAPPER.readTree(read.body).at("/data/inventory").size()).isZero();
    }

    @Test
    void writeEndpointsRejectTheWrongMethodAndUnknownActions() throws IOException {
        assertThat(http.send("GET", "/v1/players/Alex/give", BEARER, null).status).isEqualTo(405);
        assertThat(http.send("GET", "/v1/players/Alex/reset", BEARER, null).status).isEqualTo(405);
        assertThat(http.send("POST", "/v1/players/Alex", BEARER, "{}").status).isEqualTo(405);
        assertThat(http.send("POST", "/v1/players/Alex/nothing", BEARER, "{}").status).isEqualTo(404);
    }
}
