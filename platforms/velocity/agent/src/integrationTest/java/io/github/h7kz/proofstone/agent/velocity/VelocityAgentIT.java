package io.github.h7kz.proofstone.agent.velocity;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.h7kz.proofstone.core.await.Await;
import io.github.h7kz.proofstone.core.cell.CellPlanner;
import io.github.h7kz.proofstone.core.cell.Scope;
import io.github.h7kz.proofstone.core.cell.TopologyCell;
import io.github.h7kz.proofstone.core.versions.VersionsCatalog;
import io.github.h7kz.proofstone.protocol.testing.GoldenMatcher;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/**
 * The Velocity agent in a real Velocity (T-M7-05): boots Velocity 4.3.1 build 45 from the pinned {@code itzg/mc-proxy}
 * image with the shaded agent jar, then calls {@code health}, {@code proxy/servers}, {@code command} and {@code log}.
 * No backend runs, so every configured server is unreachable. {@code players} and {@code events} with a joined player
 * are NOT claimed here: they need a backend and belong to T-M7-11.
 *
 * <p>Everything the container holds is copied in (ADR-0005, no bind mount). The Velocity jar is downloaded by the image
 * from the pinned build and its sha256 is checked in the container before any test runs. The forwarding secret and the
 * agent token are generated here, in memory (ADR-0016 section 8). The agent port is published for this test only.
 * The proxy is not a Minecraft server, so the EULA variable is not needed.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class VelocityAgentIT {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Path ROOT = Path.of(System.getProperty("proofstone.repoRoot"));
    private static final Path GOLDEN = ROOT.resolve("proofstone-protocol/golden");
    private static final TopologyCell CELL = plannedCell();
    private static final String IMAGE = CELL.proxy().hostImage() + "@" + CELL.proxy().hostDigest();
    private static final String VERSION = CELL.proxy().version();
    private static final String BUILD = Integer.toString(CELL.proxy().build());
    private static final String JAR_SHA256 = CELL.proxy().sha256();
    private static final Path AGENT_JAR = Path.of(System.getProperty("proofstone.agentJar"));
    private static final int AGENT_PORT = 25580;
    private static final Duration BOOT_TIMEOUT = Duration.ofMinutes(3);
    private static final Duration AGENT_TIMEOUT = Duration.ofSeconds(30);

    /** The topology cell the pins come from: versions.yml schema 3, not this file (T-M7-08). */
    private static TopologyCell plannedCell() {
        VersionsCatalog catalog = VersionsCatalog.load(ROOT.resolve("versions.yml"),
                ROOT.resolve("proofstone-protocol/schemas/versions.schema.json"));
        return new CellPlanner(catalog).planTopology("velocity", "latest", "paper", "latest", Scope.NIGHTLY,
                TopologyCell.DEFAULT_BACKENDS);
    }

    private final HttpClient http = HttpClient.newHttpClient();
    private final String token = randomHex(24);
    private GenericContainer<?> container;

    @BeforeAll
    void boot() {
        assertThat(AGENT_JAR).as("agent jar (build it with :platforms:velocity:agent:shadowJar)").isRegularFile();
        container = new GenericContainer<>(DockerImageName.parse(IMAGE))
                .withEnv("TYPE", "VELOCITY")
                .withEnv("VELOCITY_VERSION", VERSION)
                .withEnv("VELOCITY_BUILD_ID", BUILD)
                // The image would otherwise download Velocircon and fetch default configs from GitHub (ADR-0016 section 2).
                .withEnv("ENABLE_RCON", "false")
                .withEnv("SKIP_DOWNLOAD_DEFAULTS", "true")
                .withEnv("PROOFSTONE_AGENT_TOKEN", token)
                .withEnv("PROOFSTONE_AGENT_PORT", Integer.toString(AGENT_PORT))
                .withCopyToContainer(Transferable.of(velocityToml()), "/server/velocity.toml")
                .withCopyToContainer(Transferable.of(randomHex(32)), "/server/forwarding.secret")
                .withCopyToContainer(Transferable.of("enabled=false\n"), "/server/plugins/bStats/config.txt")
                .withCopyFileToContainer(MountableFile.forHostPath(AGENT_JAR), "/server/plugins/proofstone-agent.jar")
                .withExposedPorts(AGENT_PORT);
        try {
            container.start();
            await("the Velocity log shows \"Done (\"", BOOT_TIMEOUT, () -> {
                requireRunning();
                return container.getLogs().contains("Done (");
            });
            await("agent /v1/health OK", AGENT_TIMEOUT, () -> {
                requireRunning();
                return call("GET", "/v1/health", null, true).statusCode() == 200;
            });
            verifyPins();
        } catch (RuntimeException | AssertionError e) {
            String tail = tail();
            shutdown();
            throw new IllegalStateException("Velocity did not become ready: " + e.getMessage()
                    + "\n--- proxy log tail ---\n" + tail, e);
        }
    }

    @AfterAll
    void shutdown() {
        if (container != null) {
            saveLog();
            container.stop();
            container = null;
        }
    }

    /** A proxy with three unreachable servers and the harness defaults of ADR-0016 section 5. Modern forwarding. */
    private static String velocityToml() {
        return """
                config-version = "2.9"
                bind = "0.0.0.0:25577"
                motd = "proofstone agent test"
                show-max-players = 500
                online-mode = false
                force-key-authentication = false
                prevent-client-proxy-connections = false
                player-info-forwarding-mode = "modern"
                forwarding-secret-file = "forwarding.secret"
                announce-forge = false
                kick-existing-players = false

                [servers]
                lobby = "127.0.0.1:25565"
                survival = "127.0.0.1:25566"
                try = ["lobby"]

                [forced-hosts]

                [advanced]
                login-ratelimit = 0
                command-rate-limit = 0
                log-player-connections = true
                """;
    }

    /** The pins must be what actually runs (ADR-0016 section 2): the jar matches its sha256, on the pinned Java. */
    private void verifyPins() {
        String sums = exec("sh", "-c", "sha256sum /server/velocity-*.jar");
        assertThat(sums).as("proxy jar sha256").contains(JAR_SHA256);
        assertThat(exec("java", "-version")).as("host java").contains("25");
    }

    // The golden cases the proxy can answer without a backend or a player.

    @Test
    void healthMatchesTheProxyGolden() throws IOException {
        JsonNode golden = golden("health/proxy-ok");
        // The agent answers for more tokens than the golden's minimum (command, log). Compare the rest by shape.
        JsonNode actual = JSON.readTree(call("GET", "/v1/health", null, true).body()).get("data");
        List<String> tokens = new ArrayList<>();
        actual.get("capabilities").forEach(token -> tokens.add(token.asText()));
        List<String> required = new ArrayList<>();
        golden.get("body").get("data").get("capabilities").forEach(token -> required.add(token.asText()));
        assertThat(tokens).containsAll(required).doesNotContain("events");
        ObjectNode data = (ObjectNode) golden.get("body").get("data");
        data.putArray("capabilities");
        tokens.forEach(((com.fasterxml.jackson.databind.node.ArrayNode) data.get("capabilities"))::add);
        assertMatches("health/proxy-ok", golden, call("GET", "/v1/health", null, true));
        assertThat(actual.get("mcVersion").asText()).startsWith(VERSION);
        assertThat(actual.get("platform").asText()).isEqualTo("velocity");
    }

    @Test
    void aMissingTokenMatchesTheUnauthorisedGolden() throws IOException {
        JsonNode golden = golden("health/unauthorised");
        assertMatches("health/unauthorised", golden, call("GET", "/v1/health", null, false));
    }

    @Test
    void commandRunsOnTheProxyAndReturnsItsOutput() throws IOException {
        // The golden's "version" is a Paper command. Velocity's is "velocity info"; the reply shape is the same.
        JsonNode golden = golden("command/reply");
        HttpResponse<String> reply = call("POST", "/v1/command",
                "{\"command\":\"velocity info\",\"timeoutMs\":5000}", true);
        assertMatches("command/reply", golden, reply);
        assertThat(JSON.readTree(reply.body()).get("data").get("raw").asText()).contains("Velocity").contains(VERSION);
    }

    @Test
    void anUnknownCommandIsAnErrorNotAnEmptyReply() throws IOException {
        HttpResponse<String> reply = call("POST", "/v1/command",
                "{\"command\":\"proofstone-no-such-command\",\"timeoutMs\":5000}", true);
        assertThat(reply.statusCode()).isEqualTo(400);
        assertThat(JSON.readTree(reply.body()).get("error").get("code").asText()).isEqualTo("INVALID_REQUEST");
    }

    @Test
    void serversMatchTheGoldenForABackendThatIsDown() throws IOException {
        // The golden was recorded with both backends up (online true, one player on lobby). Nothing runs behind this
        // proxy, so the same shape must report online false and no players: the ping is the proxy's own.
        JsonNode golden = golden("proxy/servers");
        for (JsonNode server : golden.get("body").get("data").get("servers")) {
            ((ObjectNode) server).put("online", false).put("players", 0);
        }
        HttpResponse<String> reply = call("GET", "/v1/proxy/servers", null, true);
        assertMatches("proxy/servers", golden, reply);
        JsonNode servers = JSON.readTree(reply.body()).get("data").get("servers");
        assertThat(servers.get(0).get("address").asText()).isEqualTo("127.0.0.1:25565");
        assertThat(servers.get(1).get("address").asText()).isEqualTo("127.0.0.1:25566");
    }

    @Test
    void oneServerMatchesTheGoldenForABackendThatIsDown() throws IOException {
        JsonNode golden = golden("proxy/server-get");
        ((ObjectNode) golden.get("body").get("data")).put("online", false);
        assertMatches("proxy/server-get", golden, call("GET", "/v1/proxy/servers/survival", null, true));
    }

    @Test
    void anUnknownServerMatchesTheGolden() throws IOException {
        assertMatches("proxy/server-not-found", golden("proxy/server-not-found"),
                call("GET", "/v1/proxy/servers/ghost", null, true));
    }

    @Test
    void anEmptyProxyHasNoPlayersAndAnUnknownPlayerMatchesTheGolden() throws IOException {
        // No client joins in this test, so this checks the empty proxy only. A joined player's proxy state is T-M7-11.
        JsonNode list = JSON.readTree(call("GET", "/v1/players", null, true).body()).get("data").get("players");
        assertThat(list).isEmpty();
        assertMatches("players/not-found", golden("players/not-found"), call("GET", "/v1/players/Nobody", null, true));
    }

    @Test
    void theLogHoldsVelocitysOwnLinesWithTheProtocolShape() throws IOException {
        HttpResponse<String> reply = call("GET", "/v1/log?since=0&minLevel=INFO", null, true);
        assertThat(reply.statusCode()).isEqualTo(200);
        JsonNode data = JSON.readTree(reply.body()).get("data");
        JsonNode records = data.get("records");
        // The agent attaches while Velocity initialises, so the start-up lines that follow are in the buffer.
        assertThat(records).as("log records").isNotEmpty();
        boolean sawDone = false;
        for (JsonNode record : records) {
            String thrown = record.get("thrown").isNull() ? "null" : "\"<string>\"";
            String expected = "{\"time\":\"<time>\",\"level\":" + record.get("level") + ",\"logger\":\"<string>\","
                    + "\"message\":\"<string>\",\"thrown\":" + thrown + "}";
            assertThat(GoldenMatcher.mismatches(expected, record.toString())).as(record.toString()).isEmpty();
            sawDone |= record.get("message").asText().startsWith("Done (");
        }
        assertThat(sawDone).as("Velocity's own \"Done (\" line was captured").isTrue();
        assertThat(data.get("next").asLong()).isGreaterThanOrEqualTo(records.size());
        // Velocity's Log4j bridge refuses a JUL handler with a WARN of its own (run A showed it), so the agent must not add one.
        assertThat(container.getLogs()).as("the agent must not add a JUL handler").doesNotContain("addHandler");
        // A floor above INFO leaves out the INFO lines: the level filter works on real records.
        JsonNode errors = JSON.readTree(call("GET", "/v1/log?since=0&minLevel=ERROR", null, true).body())
                .get("data").get("records");
        assertThat(errors.size()).isLessThan(records.size());
    }

    @Test
    void gameStateEndpointsAnswerNotFound() throws IOException {
        HttpResponse<String> reply = call("GET", "/v1/ticks", null, true);
        assertThat(reply.statusCode()).isEqualTo(404);
        assertThat(JSON.readTree(reply.body()).get("error").get("code").asText()).isEqualTo("NOT_FOUND");
    }

    // Helpers.

    private void assertMatches(String name, JsonNode expected, HttpResponse<String> actual) {
        assertThat(actual.statusCode()).as(name + " status").isEqualTo(expected.get("status").asInt());
        assertThat(GoldenMatcher.mismatches(expected.get("body").toString(), actual.body()))
                .as(name + " against the golden").isEmpty();
    }

    private JsonNode golden(String name) throws IOException {
        JsonNode request = JSON.readTree(GOLDEN.resolve(name + ".request.json").toFile());
        JsonNode response = JSON.readTree(GOLDEN.resolve(name + ".response.json").toFile());
        assertThat(request.has("method")).isTrue();
        return response;
    }

    private HttpResponse<String> call(String method, String path, String body, boolean auth) {
        HttpRequest.Builder request = HttpRequest
                .newBuilder(URI.create("http://" + container.getHost() + ":" + container.getMappedPort(AGENT_PORT) + path))
                .timeout(Duration.ofSeconds(20))
                .header("X-Proofstone-Request-Id", UUID.randomUUID().toString());
        if (auth) {
            request.header("Authorization", "Bearer " + token);
        }
        request.method(method, body == null
                ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        try {
            return http.send(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted", e);
        }
    }

    private static void await(String what, Duration timeout, java.util.function.Supplier<Boolean> probe) {
        Await.until(what, probe, Boolean::booleanValue, timeout);
    }

    private void requireRunning() {
        if (!container.isRunning()) {
            throw new IllegalStateException("container exited");
        }
    }

    private String exec(String... command) {
        try {
            var result = container.execInContainer(command);
            return result.getStdout() + result.getStderr();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted", e);
        }
    }

    private String tail() {
        String[] lines = container.getLogs().split("\n");
        int from = Math.max(0, lines.length - 80);
        return String.join("\n", java.util.Arrays.copyOfRange(lines, from, lines.length));
    }

    /** Keeps the proxy log next to the other build output, so a red run leaves its evidence. */
    private void saveLog() {
        try {
            Path dir = AGENT_JAR.getParent().getParent().resolve("velocity-it");
            Files.createDirectories(dir);
            Files.writeString(dir.resolve("proxy.log"), container.getLogs());
        } catch (IOException | RuntimeException e) {
            System.err.println("could not save the proxy log: " + e);
        }
    }

    private static String randomHex(int bytes) {
        byte[] random = new byte[bytes];
        new SecureRandom().nextBytes(random);
        return HexFormat.of().formatHex(random);
    }
}
