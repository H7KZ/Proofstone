package dev.proofstone.agent.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.proofstone.core.await.Await;
import dev.proofstone.core.cell.Cell;
import dev.proofstone.core.cell.CellPlanner;
import dev.proofstone.core.driver.ClientSession;
import dev.proofstone.core.driver.ConnectSpec;
import dev.proofstone.core.host.Eula;
import dev.proofstone.core.host.PaperHost;
import dev.proofstone.core.host.PaperHostOptions;
import dev.proofstone.core.versions.VersionsCatalog;
import dev.proofstone.driver.mcpl.McplDriver;
import dev.proofstone.protocol.testing.GoldenMatcher;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublisher;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.TestInstance;

/**
 * Replays the golden cases (proofstone-protocol/golden) against the agent in a real Paper server: the newest row of
 * versions.yml (T-M1-05, ADR-0002, ADR-0005). Every reply is compared with {@link GoldenMatcher}.
 *
 * <p>The agent port is published for this test only, through PaperHost debug mode, so the host can call it. Cases
 * about {@code Alex} join the server with the mcpl driver first and then reset him, as golden/README.md says. Needs
 * Docker and an accepted EULA ({@value Eula#VARIABLE}=true). Without the EULA the class is skipped with that reason,
 * so a skipped run is never read as a pass.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AgentContractIT {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Path ROOT = Path.of(System.getProperty("proofstone.repoRoot"));
    private static final Path GOLDEN = ROOT.resolve("proofstone-protocol/golden");
    private static final String PLAYER = "Alex";
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration JOIN_TIMEOUT = Duration.ofSeconds(60);

    /**
     * Every golden case this agent serves, in the order golden/README.md replays them. The players cases depend on
     * their order, and events/subscribe must come before the first Alex join so that events/poll sees it. log/read
     * expects exactly one WARN, which the fixture plugin logs on enable, so it must replay before the reload cases
     * enable the fixture again.
     */
    static final List<String> CASES = List.of(
            "health/ok",
            "health/unauthorised",
            "command/reply",
            "events/subscribe",
            "players/not-found",
            "players/empty-inventory",
            "players/give-enchanted-sword",
            "players/read-after-give",
            "events/poll",
            "world/block-get",
            "world/block-put",
            "log/read",
            "plugins/config-patch",
            "plugins/config-read",
            "plugins/config-restore",
            "plugins/config-read-after-restore",
            "plugins/reload",
            "plugins/reload-hook",
            "ticks/read",
            "ticks/await");

    /**
     * Golden cases for endpoints the agent does not serve yet. The openWindow card moves players/open-anvil into
     * {@link #CASES}. The list is explicit so a new case is never silently ignored:
     * {@link #everyGoldenCaseIsReplayed()} still fails for any case in neither list.
     */
    static final List<String> PENDING = List.of(
            "players/open-anvil");

    private final HttpClient http = HttpClient.newHttpClient();
    private PaperHost host;
    private ClientSession alex;

    @BeforeAll
    void bootPaper() {
        assumeTrue(Eula.accepted(), "Minecraft EULA not accepted: set " + Eula.VARIABLE + "=true");
        VersionsCatalog catalog = VersionsCatalog.load(ROOT.resolve("versions.yml"),
                ROOT.resolve("proofstone-protocol/schemas/versions.schema.json"));
        Cell cell = new CellPlanner(catalog).plan("paper", List.of("latest")).get(0);
        // The fixture plugin (platforms/bukkit/fixture) carries the config.yml the config cases read and patch.
        Path fixture = Path.of(System.getProperty("proofstone.fixtureJar"));
        PaperHostOptions options = PaperHostOptions.of(Path.of(System.getProperty("proofstone.agentJar")), true)
                .withArtefacts(fixture.getParent(), List.of(fixture.getFileName().toString()))
                .withDebugPorts(true);
        host = PaperHost.start(cell, options);
        // golden/README.md: the reset lands Alex on (0.5, 64.0, 0.5). The spawn is block (0, 63, 0), and a stone block
        // under it holds the player at y 64, so he does not fall between the reset and the read.
        // Right after boot the spawn chunk is not loaded, and setblock answers "That position is not loaded" (the
        // reply is not an error, so it must be checked by the block read below). forceload keeps chunk 0,0 loaded.
        host.command("forceload add 0 0");
        host.command("setblock 0 63 0 minecraft:stone");
        host.command("setworldspawn 0 63 0");
    }

    @AfterAll
    void stopPaper() {
        if (alex != null) {
            alex.close();
        }
        if (host != null) {
            host.close();
        }
    }

    /** Fails when a golden case is added without being replayed here (golden/README.md, Adding a case). */
    @Test
    void everyGoldenCaseIsReplayed() throws IOException {
        Set<String> onDisk;
        try (Stream<Path> files = Files.walk(GOLDEN)) {
            onDisk = files
                    .filter(file -> file.getFileName().toString().endsWith(".request.json"))
                    .map(file -> GOLDEN.relativize(file).toString().replace('\\', '/')
                            .replaceFirst("\\.request\\.json$", ""))
                    .collect(Collectors.toSet());
        }
        Set<String> known = new java.util.HashSet<>(CASES);
        known.addAll(PENDING);
        assertThat(onDisk).containsExactlyInAnyOrderElementsOf(known);
    }

    @TestFactory
    Stream<DynamicTest> goldenCases() {
        return CASES.stream().map(name -> dynamicTest(name, () -> replay(name)));
    }

    private void replay(String name) throws Exception {
        JsonNode request = JSON.readTree(GOLDEN.resolve(name + ".request.json").toFile());
        JsonNode expected = JSON.readTree(GOLDEN.resolve(name + ".response.json").toFile());
        String path = request.get("path").asText();
        if (path.startsWith("/v1/players/" + PLAYER)) {
            joinAndResetPlayer();
        }
        String body = request.has("body") ? JSON.writeValueAsString(request.get("body")) : null;
        Reply actual = send(request.get("method").asText(), path, request.path("auth").asBoolean(true), body);

        assertThat(actual.status()).as(name + " status; body was " + actual.body())
                .isEqualTo(expected.get("status").asInt());
        assertThat(GoldenMatcher.mismatches(expected.get("body").toString(), actual.body()))
                .as(name + " differs from its golden response")
                .isEmpty();
    }

    /** Joins Alex with the mcpl driver once, waits until the agent sees him, then resets him (golden/README.md). */
    private void joinAndResetPlayer() throws IOException, InterruptedException {
        if (alex != null) {
            return;
        }
        alex = new McplDriver().connect(new ConnectSpec(host.gameHost(), host.gamePort(), PLAYER, JOIN_TIMEOUT));
        Await.until(PLAYER + " online per the agent", this::playerStatus, status -> status == 200, JOIN_TIMEOUT);
        Reply reset = send("POST", "/v1/players/" + PLAYER + "/reset", true, null);
        assertThat(reset.status()).as("reset " + PLAYER + "; body was " + reset.body()).isEqualTo(200);
    }

    private int playerStatus() {
        try {
            return send("GET", "/v1/players/" + PLAYER, true, null).status();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while waiting for " + PLAYER, e);
        }
    }

    private Reply send(String method, String path, boolean auth, String body)
            throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(
                        URI.create("http://127.0.0.1:" + host.debugAgentPort() + path))
                .timeout(HTTP_TIMEOUT);
        if (auth) {
            builder.header("Authorization", "Bearer " + host.agentToken());
        }
        BodyPublisher publisher = BodyPublishers.noBody();
        if (body != null) {
            builder.header("Content-Type", "application/json; charset=utf-8");
            publisher = BodyPublishers.ofString(body, StandardCharsets.UTF_8);
        }
        HttpResponse<String> response = http.send(
                builder.method(method, publisher).build(), BodyHandlers.ofString(StandardCharsets.UTF_8));
        return new Reply(response.statusCode(), response.body());
    }

    private record Reply(int status, String body) {
    }
}
