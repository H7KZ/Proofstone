package io.github.h7kz.proofstone.agent.fabric;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.h7kz.proofstone.core.await.Await;
import io.github.h7kz.proofstone.core.cell.CellPlanner;
import io.github.h7kz.proofstone.core.cell.LoaderCell;
import io.github.h7kz.proofstone.core.cell.Scope;
import io.github.h7kz.proofstone.core.driver.ClientSession;
import io.github.h7kz.proofstone.core.driver.ConnectSpec;
import io.github.h7kz.proofstone.core.host.Eula;
import io.github.h7kz.proofstone.core.versions.VersionsCatalog;
import io.github.h7kz.proofstone.driver.mcpl.McplDriver;
import io.github.h7kz.proofstone.protocol.testing.GoldenMatcher;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/**
 * The Fabric agent in a real Fabric server (T-M8-05): the fabric@26.3 row of versions.yml (loader 0.20.1, launcher
 * installer 1.1.2, Fabric API 0.166.2+26.3) on the pinned {@code itzg/minecraft-server} java25 digest, with the shaded
 * agent jar. The mcpl driver joins {@code Alex}, and the golden cases a Fabric server serves are replayed over HTTP.
 *
 * <p>Inputs are copied in (ADR-0005, no bind mount). The launcher and Fabric API are downloaded from Fabric's servers into
 * {@code build/fabric-it/cache}, checked by the row's sha256 before the copy, and never committed. At a cold start the
 * launcher fetches the vanilla server jar from Mojang (ADR-0017 section 6). Offline mode, so no session server.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FabricAgentIT {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Path ROOT = Path.of(System.getProperty("proofstone.repoRoot"));
    private static final Path GOLDEN = ROOT.resolve("proofstone-protocol/golden");
    private static final Path AGENT_JAR = Path.of(System.getProperty("proofstone.agentJar"));
    private static final Path WORK = AGENT_JAR.getParent().getParent().resolve("fabric-it");
    private static final LoaderCell CELL = plannedCell();
    private static final String PLAYER = "Alex";
    private static final String JOIN_EVENT = "net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.JOIN";
    private static final int AGENT_PORT = 25580;
    private static final int GAME_PORT = 25565;
    private static final Duration BOOT_TIMEOUT = Duration.ofMinutes(5);
    private static final Duration AGENT_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration JOIN_TIMEOUT = Duration.ofSeconds(60);

    /** The loader cell the pins come from: versions.yml schema 4, not this file (ADR-0017 section 3). */
    private static LoaderCell plannedCell() {
        VersionsCatalog catalog = VersionsCatalog.load(ROOT.resolve("versions.yml"),
                ROOT.resolve("proofstone-protocol/schemas/versions.schema.json"));
        return new CellPlanner(catalog).planLoaders("fabric", List.of("26.3"), Scope.NIGHTLY).get(0);
    }

    private final HttpClient http = HttpClient.newHttpClient();
    private final String token = HexFormat.of().formatHex(new SecureRandom().generateSeed(24));
    private GenericContainer<?> container;
    private ClientSession alex;
    private String joinProbe;
    private Path launcher;

    @BeforeAll
    void boot() throws IOException, InterruptedException {
        assumeTrue(Eula.accepted(), "Minecraft EULA not accepted: set " + Eula.VARIABLE + "=true");
        assertThat(AGENT_JAR).as("agent jar (build it with :platforms:fabric:agent:shadowJar)").isRegularFile();
        assertThat(CELL.fabricApi().sha256()).as("Fabric API sha256 in versions.yml").isNotNull();
        String launcherName = "fabric-server-mc." + CELL.mc() + "-loader." + CELL.loaderVersion() + "-launcher."
                + CELL.installer() + ".jar";
        launcher = pinned(launcherName, "https://meta.fabricmc.net/v2/versions/loader/" + CELL.mc() + "/"
                + CELL.loaderVersion() + "/" + CELL.installer() + "/server/jar", CELL.sha256());
        String api = CELL.fabricApi().version().replace("+", "%2B");
        Path fabricApi = pinned("fabric-api-" + CELL.fabricApi().version() + ".jar",
                "https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/" + api + "/fabric-api-" + api + ".jar",
                CELL.fabricApi().sha256());
        container = new GenericContainer<>(DockerImageName.parse(CELL.hostImage() + "@" + CELL.hostDigest()))
                .withEnv("EULA", "TRUE")
                .withEnv("TYPE", "FABRIC")
                .withEnv("VERSION", CELL.mc())
                .withEnv("FABRIC_LAUNCHER", "/pins/" + launcherName)
                .withEnv("ONLINE_MODE", "FALSE")
                .withEnv("ENFORCE_SECURE_PROFILE", "FALSE")
                .withEnv("LEVEL_TYPE", "minecraft:flat")
                .withEnv("ENABLE_RCON", "false")
                .withEnv("PROOFSTONE_AGENT_TOKEN", token)
                .withEnv("PROOFSTONE_AGENT_PORT", Integer.toString(AGENT_PORT))
                .withCopyFileToContainer(MountableFile.forHostPath(launcher), "/pins/" + launcherName)
                .withCopyFileToContainer(MountableFile.forHostPath(fabricApi), "/mods/fabric-api.jar")
                .withCopyFileToContainer(MountableFile.forHostPath(AGENT_JAR), "/mods/proofstone-agent.jar")
                .withExposedPorts(AGENT_PORT, GAME_PORT)
                .waitingFor(Wait.forLogMessage(".*Done \\(.*", 1).withStartupTimeout(BOOT_TIMEOUT));
        try {
            container.start();
            await("agent /v1/health OK", AGENT_TIMEOUT, () -> {
                requireRunning();
                return call("GET", "/v1/health", null, true).statusCode() == 200;
            });
            verifyPins(launcherName, CELL.fabricApi().sha256());
            setUpWorldAndJoin();
        } catch (RuntimeException | AssertionError e) {
            String tail = tail();
            shutdown();
            throw new IllegalStateException("Fabric did not become ready: " + e.getMessage()
                    + "\n--- server log tail ---\n" + tail, e);
        }
    }

    @AfterAll
    void shutdown() {
        if (alex != null) {
            alex.close();
            alex = null;
        }
        if (container != null) {
            saveLog();
            container.stop();
            container = null;
        }
    }

    /**
     * golden/README.md fixture: spawn block (0, 63, 0) of stone, so a reset lands Alex on (0.5, 64.0, 0.5). The probe on
     * Fabric API's join callback is subscribed before Alex joins, so it must see him. Console commands are setup only.
     */
    private void setUpWorldAndJoin() {
        assertThat(command("forceload add 0 0")).contains("0, 0");
        assertThat(call("PUT", "/v1/world/block?world=world&x=0&y=63&z=0", "{\"type\":\"minecraft:stone\"}", true)
                .statusCode()).isEqualTo(200);
        assertThat(command("setworldspawn 0 63 0")).isNotBlank();
        joinProbe = data(call("POST", "/v1/events/subscribe", "{\"class\":\"" + JOIN_EVENT + "\"}", true))
                .get("probeId").asText();
        alex = new McplDriver().connect(new ConnectSpec(container.getHost(), container.getMappedPort(GAME_PORT),
                PLAYER, JOIN_TIMEOUT));
        await(PLAYER + " online per the agent", JOIN_TIMEOUT,
                () -> call("GET", "/v1/players/" + PLAYER, null, true).statusCode() == 200);
    }

    /** The pins must be what actually runs: launcher and Fabric API by sha256 in the container, on Java 25. */
    private void verifyPins(String launcherName, String fabricApiSha256) {
        assertThat(exec("sha256sum", "/pins/" + launcherName)).as("launcher sha256").contains(CELL.sha256());
        assertThat(exec("sh", "-c", "sha256sum /data/mods/*.jar")).as("Fabric API in /data/mods").contains(fabricApiSha256);
        assertThat(exec("java", "-version")).as("host java").contains("25");
    }

    @Test
    void healthMatchesTheGoldenAsAFabricAgent() throws IOException {
        JsonNode golden = golden("health/ok");
        ObjectNode data = (ObjectNode) golden.get("body").get("data");
        data.put("platform", "fabric");
        ArrayNode tokens = data.putArray("capabilities");
        List.of("command", "players", "world", "events", "log", "ticks", "mods", "datapacks").forEach(tokens::add);
        HttpResponse<String> reply = call("GET", "/v1/health", null, true);
        assertMatches("health/ok", golden, reply);
        assertThat(data(reply).get("mcVersion").asText()).isEqualTo(CELL.mc());
    }

    @Test
    void aMissingTokenMatchesTheUnauthorisedGolden() throws IOException {
        assertMatches("health/unauthorised", golden("health/unauthorised"), call("GET", "/v1/health", null, false));
    }

    @Test
    void modsListsFabricApiAndTheAgentAsTheGoldenSays() throws IOException {
        HttpResponse<String> reply = call("GET", "/v1/mods", null, true);
        assertMatches("mods/list", golden("mods/list"), reply);
        assertThat(data(reply).get("mods").get(0).get("version").asText()).isEqualTo(CELL.fabricApi().version());
    }

    @Test
    void datapacksListsVanillaAndTheLoadersOwnPackAsTheGoldenSays() throws IOException {
        HttpResponse<String> reply = call("GET", "/v1/datapacks", null, true);
        // The golden's ids are Paper's (pinned on a real Paper server); the shape is replayed with this server's ids.
        JsonNode golden = golden("datapacks/list");
        ObjectNode expected = (ObjectNode) golden.get("body").get("data");
        expected.set("enabled", data(reply).get("enabled"));
        expected.set("available", data(reply).get("available"));
        assertMatches("datapacks/list", golden, reply);
        assertThat(data(reply).get("enabled").get(0).asText()).isEqualTo("vanilla");
        assertThat(data(reply).get("enabled").toString()).as("the server's enabled packs").doesNotContain("file/");
    }

    @Test
    void commandRunsOnTheServerAndUnknownCommandsAreErrors() throws IOException {
        // The golden's "version" is a Paper command; "list" is vanilla's, with the same reply shape.
        HttpResponse<String> reply = call("POST", "/v1/command", "{\"command\":\"list\",\"timeoutMs\":5000}", true);
        assertMatches("command/reply", golden("command/reply"), reply);
        assertThat(data(reply).get("raw").asText()).contains(PLAYER);
        HttpResponse<String> unknown = call("POST", "/v1/command", "{\"command\":\"proofstone-no-such-command\"}", true);
        assertThat(unknown.statusCode()).isEqualTo(400);
        assertThat(JSON.readTree(unknown.body()).get("error").get("code").asText()).isEqualTo("INVALID_REQUEST");
    }

    @Test
    void theFixturePlayerMatchesTheGoldensThroughResetGiveReadAndSnapshot() throws IOException {
        assertMatches("players/not-found", golden("players/not-found"), call("GET", "/v1/players/Nobody", null, true));
        assertThat(call("POST", "/v1/players/" + PLAYER + "/reset", null, true).statusCode()).isEqualTo(200);
        replay("players/empty-inventory");
        replay("players/give-enchanted-sword");
        replay("players/read-after-give");
        JsonNode snapshot = golden("snapshot/read");
        ((ObjectNode) snapshot.get("body").get("data")).putArray("plugins");
        assertMatches("snapshot/read", snapshot, call("GET", "/v1/snapshot", null, true));
        // An unknown enchantment is refused before anything changes.
        HttpResponse<String> bad = call("POST", "/v1/players/" + PLAYER + "/give",
                "{\"type\":\"minecraft:stone\",\"amount\":1,\"enchants\":{\"minecraft:no_such\":1}}", true);
        assertThat(bad.statusCode()).isEqualTo(400);
        assertThat(JSON.readTree(bad.body()).get("error").get("code").asText()).isEqualTo("UNKNOWN_KEY");
        replay("players/read-after-give");
    }

    @Test
    void blocksMatchTheGoldens() throws IOException {
        replay("world/block-get");
        replay("world/block-put");
        // An anvil cannot face up: refused with the key named, and the stone under the spawn is untouched.
        HttpResponse<String> bad = call("PUT", "/v1/world/block?world=world&x=0&y=63&z=0",
                "{\"type\":\"minecraft:anvil\",\"state\":{\"facing\":\"up\"}}", true);
        assertThat(bad.statusCode()).isEqualTo(400);
        JsonNode error = JSON.readTree(bad.body()).get("error");
        assertThat(error.get("code").asText()).isEqualTo("UNKNOWN_KEY");
        assertThat(error.get("detail").get("key").asText()).isEqualTo("minecraft:anvil[facing=up]");
        replay("world/block-get");
    }

    @Test
    void ticksMatchTheGoldensAndAdvance() throws IOException {
        replay("ticks/read");
        long before = data(call("GET", "/v1/ticks", null, true)).get("global").asLong();
        replay("ticks/await");
        assertThat(data(call("GET", "/v1/ticks", null, true)).get("global").asLong()).isGreaterThan(before);
    }

    @Test
    void theJoinProbeSawAlexOnTheServerThread() throws IOException {
        JsonNode events = data(call("GET", "/v1/events/" + joinProbe + "?since=0", null, true)).get("events");
        assertThat(events).as("join events").isNotEmpty();
        JsonNode join = events.get(0);
        assertThat(join.get("class").asText()).isEqualTo(JOIN_EVENT);
        assertThat(join.get("thread").asText()).isEqualTo("Server thread");
        assertThat(join.get("fields").get("arg0").get("name").asText()).isEqualTo(PLAYER);
    }

    @Test
    void theLogHoldsTheServersOwnLinesWithTheProtocolShape() throws IOException {
        JsonNode data = data(call("GET", "/v1/log?since=0&minLevel=INFO", null, true));
        JsonNode records = data.get("records");
        assertThat(records).as("log records").isNotEmpty();
        boolean sawDone = false;
        for (JsonNode record : records) {
            String thrown = record.get("thrown").isNull() ? "null" : "\"<string>\"";
            String expected = "{\"time\":\"<time>\",\"level\":" + record.get("level") + ",\"logger\":\"<string>\","
                    + "\"message\":\"<string>\",\"thrown\":" + thrown + "}";
            assertThat(GoldenMatcher.mismatches(expected, record.toString())).as(record.toString()).isEmpty();
            sawDone |= record.get("message").asText().startsWith("Done (");
        }
        assertThat(sawDone).as("the server's own \"Done (\" line was captured").isTrue();
    }

    @Test
    void pluginEndpointsAnswerNotFound() throws IOException {
        HttpResponse<String> reply = call("GET", "/v1/plugins/x/config?path=a", null, true);
        assertThat(reply.statusCode()).isEqualTo(404);
        assertThat(JSON.readTree(reply.body()).get("error").get("code").asText()).isEqualTo("NOT_FOUND");
    }

    // Helpers.

    private void replay(String name) throws IOException {
        JsonNode request = JSON.readTree(GOLDEN.resolve(name + ".request.json").toFile());
        String body = request.has("body") ? request.get("body").toString() : null;
        assertMatches(name, golden(name), call(request.get("method").asText(), request.get("path").asText(), body, true));
    }

    private static void assertMatches(String name, JsonNode expected, HttpResponse<String> actual) {
        assertThat(actual.statusCode()).as(name + " status; body " + actual.body()).isEqualTo(expected.get("status").asInt());
        assertThat(GoldenMatcher.mismatches(expected.get("body").toString(), actual.body()))
                .as(name + " against the golden; body " + actual.body()).isEmpty();
    }

    private static JsonNode golden(String name) throws IOException {
        return JSON.readTree(GOLDEN.resolve(name + ".response.json").toFile());
    }

    private static JsonNode data(HttpResponse<String> reply) {
        try {
            return JSON.readTree(reply.body()).get("data");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private String command(String command) {
        HttpResponse<String> reply = call("POST", "/v1/command", "{\"command\":\"" + command + "\"}", true);
        assertThat(reply.statusCode()).as(command + "; body " + reply.body()).isEqualTo(200);
        return data(reply).get("raw").asText();
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

    /**
     * A pinned input in the cache: reused when its sha256 matches, else downloaded and checked. A mismatch fails the run
     * naming the file (ADR-0017 section 3), never a silent new input.
     */
    private Path pinned(String name, String url, String sha256) throws IOException, InterruptedException {
        Path cache = WORK.resolve("cache");
        Files.createDirectories(cache);
        Path file = cache.resolve(name);
        if (Files.isRegularFile(file) && sha256(file).equals(sha256)) {
            return file;
        }
        Path part = cache.resolve(name + ".part");
        HttpResponse<InputStream> reply = http.send(HttpRequest.newBuilder(URI.create(url)).build(),
                HttpResponse.BodyHandlers.ofInputStream());
        assertThat(reply.statusCode()).as("GET " + url).isEqualTo(200);
        try (InputStream in = reply.body()) {
            Files.copy(in, part, StandardCopyOption.REPLACE_EXISTING);
        }
        assertThat(sha256(part)).as(name + " sha256 (pinned in versions.yml)").isEqualTo(sha256);
        return Files.move(part, file, StandardCopyOption.REPLACE_EXISTING);
    }

    private static String sha256(Path file) throws IOException {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
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
        if (container == null) {
            return "(no container)";
        }
        String[] lines = container.getLogs().split("\n");
        int from = Math.max(0, lines.length - 80);
        return String.join("\n", Arrays.copyOfRange(lines, from, lines.length));
    }

    /** Keeps the server log next to the other build output, so a red run leaves its evidence. */
    private void saveLog() {
        try {
            Files.createDirectories(WORK);
            Files.writeString(WORK.resolve("server.log"), container.getLogs());
            List<String> lines = new ArrayList<>(Arrays.asList(exec("sh", "-c", "ls -la /data/mods").split("\n")));
            Files.write(WORK.resolve("mods.txt"), lines);
        } catch (IOException | RuntimeException e) {
            System.err.println("could not save the server log: " + e);
        }
    }
}
