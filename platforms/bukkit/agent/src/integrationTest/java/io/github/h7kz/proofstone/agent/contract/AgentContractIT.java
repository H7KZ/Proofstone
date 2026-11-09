package io.github.h7kz.proofstone.agent.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.junit.jupiter.api.DynamicContainer.dynamicContainer;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.h7kz.proofstone.core.await.Await;
import io.github.h7kz.proofstone.core.cell.Cell;
import io.github.h7kz.proofstone.core.cell.CellPlanner;
import io.github.h7kz.proofstone.core.cell.CellSelector;
import io.github.h7kz.proofstone.core.driver.ClientSession;
import io.github.h7kz.proofstone.core.driver.ConnectSpec;
import io.github.h7kz.proofstone.core.host.Eula;
import io.github.h7kz.proofstone.core.host.PaperHost;
import io.github.h7kz.proofstone.core.host.PaperHostOptions;
import io.github.h7kz.proofstone.core.host.ViaProxyHost;
import io.github.h7kz.proofstone.core.versions.VersionsCatalog;
import io.github.h7kz.proofstone.driver.mcpl.McplDriver;
import io.github.h7kz.proofstone.protocol.testing.GoldenMatcher;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.containers.Network;

/**
 * Replays the golden cases (proofstone-protocol/golden) against the agent in a real Paper server: the newest row of
 * versions.yml (T-M1-05, ADR-0002, ADR-0005), or the cells that {@code -Dproofstone.cells} (or
 * {@code PROOFSTONE_CELLS}) selects (T-M3-06): one real server per cell, one container of cases per cell. Every reply
 * is compared with {@link GoldenMatcher}, and the same golden files serve every version.
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
    /** The platforms whose agent this test replays, in the order their cells run (the agent is one jar for all). */
    static final List<String> BUKKIT_FAMILY = List.of("paper", "folia");
    private static final String PLAYER = "Alex";
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration JOIN_TIMEOUT = Duration.ofSeconds(60);

    /**
     * Every golden case this agent serves, in the order golden/README.md replays them. The players cases depend on
     * their order, and events/subscribe must come before the first Alex join so that events/poll sees it. log/read
     * expects exactly one fixture WARN (see {@link #onlyFixtureRecords}), which the fixture plugin logs on enable, so
     * it must replay before the reload cases enable the fixture again.
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
            "snapshot/read",
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
    /** The platform of the cell that booted last; the golden files were recorded on Paper. */
    private String platform = "paper";
    private VersionsCatalog catalog;
    private PaperHost host;
    private ViaProxyHost proxy;
    private Network network;
    private ClientSession alex;

    /**
     * The cells to run: the run-time selector when it is set, otherwise the newest row. A selector that does not
     * name paper is an error, never a silent fall back to the newest row (a class that runs nothing is no pass).
     */
    List<Cell> selectedCells() {
        catalog = VersionsCatalog.load(ROOT.resolve("versions.yml"),
                ROOT.resolve("proofstone-protocol/schemas/versions.schema.json"));
        String setting = System.getProperty("proofstone.cells");
        if (setting == null || setting.isBlank()) {
            setting = System.getenv("PROOFSTONE_CELLS");
        }
        CellSelector selector = CellSelector.parse(setting, "-Dproofstone.cells or PROOFSTONE_CELLS");
        if (!selector.isSet()) {
            return new CellPlanner(catalog).plan("paper", List.of("latest"));
        }
        // Every Bukkit-family platform the selector names runs here, in the order of BUKKIT_FAMILY. A selector that
        // names none of them is an error, never a silent fall back to the newest row (a class that runs nothing is
        // no pass).
        List<Cell> cells = new ArrayList<>();
        for (String platform : BUKKIT_FAMILY) {
            if (selector.selects(platform)) {
                cells.addAll(new CellPlanner(catalog).plan(platform, selector.versions(platform)));
            }
        }
        if (cells.isEmpty()) {
            throw new IllegalStateException("-Dproofstone.cells names no Bukkit-family platform " + BUKKIT_FAMILY
                    + ", the platforms of the agent contract test: " + setting);
        }
        return cells;
    }

    private void bootPaper(Cell cell) {
        assumeTrue(Eula.accepted(), "Minecraft EULA not accepted: set " + Eula.VARIABLE + "=true");
        platform = cell.platform();
        // The fixture plugin (platforms/bukkit/fixture) carries the config.yml the config cases read and patch.
        Path fixture = Path.of(System.getProperty("proofstone.fixtureJar"));
        PaperHostOptions options = PaperHostOptions.of(Path.of(System.getProperty("proofstone.agentJar")), true)
                .withArtefacts(fixture.getParent(), List.of(fixture.getFileName().toString()))
                .withDebugPorts(true);
        McplDriver driver = new McplDriver();
        // The driver speaks one protocol; any other row is reached through ViaProxy (ADR-0004), as in CellInvocation.
        if (driver.protocolVersion().equals(cell.mc())) {
            host = PaperHost.start(cell, options);
        } else {
            network = Network.newNetwork();
            host = PaperHost.start(cell, options.withNetwork(network));
            proxy = ViaProxyHost.start(cell, catalog.driverImage("viaproxy"), network);
        }
        // golden/README.md: the reset lands Alex on (0.5, 64.0, 0.5). The spawn is block (0, 63, 0), and a stone block
        // under it holds the player at y 64, so he does not fall between the reset and the read.
        // Right after boot the spawn chunk is not loaded, and setblock answers "That position is not loaded" (the
        // reply is not an error, so it must be checked by the block read below). forceload keeps chunk 0,0 loaded.
        host.command("forceload add 0 0");
        host.command("setblock 0 63 0 minecraft:stone");
        host.command("setworldspawn 0 63 0");
    }

    private void stopPaper() {
        if (alex != null) {
            alex.close();
            alex = null;
        }
        if (proxy != null) {
            proxy.close();
            proxy = null;
        }
        if (host != null) {
            host.close();
            host = null;
        }
        if (network != null) {
            network.close();
            network = null;
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

    /**
     * One container per selected cell. The server boots as the container's first test and closes with its stream,
     * so a failed boot is reported on the cell and the other cells still run.
     */
    @TestFactory
    Stream<DynamicNode> goldenCases() {
        return selectedCells().stream().map(cell -> {
            String label = cell.platform() + "@" + cell.mc();
            Stream<DynamicNode> foliaOnly = "folia".equals(cell.platform())
                    ? Stream.of(dynamicTest("folia regions", this::regionTicks),
                            dynamicTest("no thread-check errors", this::noThreadCheckErrors))
                    : Stream.empty();
            Stream<DynamicNode> children = Stream.concat(
                    Stream.concat(
                            Stream.of(dynamicTest("boot " + label, () -> bootPaper(cell))),
                            CASES.stream().map(name -> dynamicTest(name, () -> replay(name)))),
                    // Not a golden case: the key of a renamed item is the same on every version (T-M3-06).
                    Stream.concat(Stream.of(dynamicTest("renamed item keys", this::renamedItemKeys)), foliaOnly));
            return dynamicContainer(label, children.onClose(this::stopPaper));
        });
    }

    /**
     * golden_sword is GOLD_SWORD before 1.13. It must read back under the 1.13 key, and the legacy enum name must
     * not be accepted as a key on any version, so a test passes or fails the same way on every row.
     */
    private void renamedItemKeys() throws Exception {
        assertThat(host).as("the server of this cell did not boot, see its boot test").isNotNull();
        joinAndResetPlayer();
        // The golden cases left their sword in slot 0.
        assertThat(send("POST", "/v1/players/" + PLAYER + "/reset", true, null).status()).isEqualTo(200);
        Reply refused = send("POST", "/v1/players/" + PLAYER + "/give", true,
                "{\"type\":\"minecraft:gold_sword\",\"amount\":1}");
        assertThat(refused.status()).as("gold_sword is not a key; body was " + refused.body() + " player " + send("GET", "/v1/players/" + PLAYER, true, null).body()).isEqualTo(400);
        assertThat(refused.body()).contains("UNKNOWN_KEY");

        Reply given = send("POST", "/v1/players/" + PLAYER + "/give", true,
                "{\"type\":\"minecraft:golden_sword\",\"amount\":1,\"enchants\":{\"minecraft:knockback\":2}}");
        assertThat(given.status()).as("give golden_sword; body was " + given.body()).isEqualTo(200);
        JsonNode slot = JSON.readTree(send("GET", "/v1/players/" + PLAYER, true, null).body())
                .path("data").path("inventory").path("0");
        assertThat(slot.path("type").asText()).as("slot 0 was " + slot).isEqualTo("minecraft:golden_sword");
        assertThat(slot.path("enchants").path("minecraft:knockback").asInt()).as("slot 0 was " + slot).isEqualTo(2);
    }

    private void replay(String name) throws Exception {
        assertThat(host).as("the server of this cell did not boot, see its boot test").isNotNull();
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
        String actualBody = name.equals("log/read") ? onlyFixtureRecords(actual.body()) : actual.body();
        if (name.startsWith("ticks/")) {
            actualBody = withoutRegions(actualBody);
        }
        JsonNode golden = forPlatform(name, expected);
        assertThat(GoldenMatcher.mismatches(golden.get("body").toString(), actualBody))
                .as(name + " differs from its golden response; body was " + actual.body())
                .isEmpty();
    }

    /**
     * The golden files record Paper's answers. A Folia server differs in exactly two places: its platform name, and the
     * {@code regions} capability token (docs/platforms/folia.md). Every cell runs offline (ONLINE_MODE=FALSE), so each
     * one also reports the {@code bedrock-prefix} token (SIMULATED_BEDROCK). Those are applied to the expectation here,
     * so every other field is still matched exactly. Folia's per-region ticks are checked by {@link #regionTicks()}.
     */
    private JsonNode forPlatform(String name, JsonNode expected) {
        if (!name.equals("health/ok")) {
            return expected;
        }
        ObjectNode copy = expected.deepCopy();
        ObjectNode data = (ObjectNode) copy.path("body").path("data");
        ((ArrayNode) data.get("capabilities")).add("bedrock-prefix");
        if (platform.equals("folia")) {
            data.put("platform", "folia");
            ((ArrayNode) data.get("capabilities")).add("regions");
        }
        return copy;
    }

    /** ticks.json carries {@code regions} on Folia only; the golden {@code ticks/*} cases hold the global counter. */
    private static String withoutRegions(String body) throws IOException {
        JsonNode reply = JSON.readTree(body);
        if (reply.path("data").has("regions")) {
            ((ObjectNode) reply.get("data")).remove("regions");
        }
        return JSON.writeValueAsString(reply);
    }

    /**
     * Folia's per-region tick counters (ticks.json, {@code regions}). By this case the world cases have run, so at least
     * one chunk region has a counter, and two more global ticks must advance every region's counter.
     */
    private void regionTicks() throws Exception {
        assertThat(host).as("the server of this cell did not boot, see its boot test").isNotNull();
        JsonNode before = JSON.readTree(send("GET", "/v1/ticks", true, null).body()).path("data");
        assertThat(before.has("regions")).as("ticks has no regions on folia: " + before).isTrue();
        assertThat(before.path("regions").size()).as("no region has a counter: " + before).isPositive();
        Reply awaited = send("POST", "/v1/ticks/await", true, "{\"n\":2}");
        assertThat(awaited.status()).as("await 2 ticks; body was " + awaited.body()).isEqualTo(200);
        JsonNode after = JSON.readTree(send("GET", "/v1/ticks", true, null).body()).path("data");
        before.path("regions").fieldNames().forEachRemaining(key -> assertThat(after.path("regions").path(key).asLong())
                .as("region " + key + " did not tick: before " + before + " after " + after)
                .isGreaterThanOrEqualTo(before.path("regions").path(key).asLong() + 1));
    }

    /**
     * Folia's thread checks stay on (docs/platforms/folia.md). After every case the server log must hold no thread-check
     * message, and every ERROR line must be the vanilla noise the Paper allowlist already names, so nothing unknown is
     * skipped (AGENTS.md section 3).
     */
    private void noThreadCheckErrors() {
        assertThat(host).as("the server of this cell did not boot, see its boot test").isNotNull();
        String log = host.logs().all();
        for (String marker : List.of("main thread check", "owning region thread", "off-thread", "Must use teleportAsync")) {
            assertThat(log).as("server log holds a thread-check message: " + marker).doesNotContain(marker);
        }
        List<String> errors = log.lines().filter(line -> line.contains(" ERROR]")).toList();
        assertThat(errors).as("ERROR lines other than the vanilla noise").allMatch(
                // The two known startup lines: vanilla's flat-world noise, and the harness's bukkit.yml copy that the
                // server cannot save (docs/platforms/paper.md, Gotchas). Anything else fails.
                line -> line.contains("No key layers in MapLike") || line.contains("Could not save bukkit.yml"));
    }

    /**
     * The golden log case holds the fixture plugin's one WARN. The agent also reads Log4j (T-M2-08), so the real
     * server's own boot warnings (offline mode, and whatever a joined player provokes) are in the reply too and
     * vary by version. The reply is narrowed to the fixture's record before it is matched (by its message: the logger
     * name of a plugin logger is the plugin name on 26.3 but the class name on 1.8.8, the golden says only
     * {@code <string>}), which still checks the
     * record shape and that the fixture's record arrived exactly once.
     */
    private static String onlyFixtureRecords(String body) throws IOException {
        JsonNode reply = JSON.readTree(body);
        com.fasterxml.jackson.databind.node.ArrayNode kept = JSON.createArrayNode();
        for (JsonNode record : reply.path("data").path("records")) {
            if (record.path("message").asText().contains("fixture warning for the log contract")) {
                kept.add(record);
            }
        }
        ((com.fasterxml.jackson.databind.node.ObjectNode) reply.get("data")).set("records", kept);
        return JSON.writeValueAsString(reply);
    }

    /** Joins Alex with the mcpl driver once, waits until the agent sees him, then resets him (golden/README.md). */
    private void joinAndResetPlayer() throws IOException, InterruptedException {
        if (alex != null) {
            return;
        }
        String address = proxy == null ? host.gameHost() : proxy.gameHost();
        int port = proxy == null ? host.gamePort() : proxy.gamePort();
        alex = new McplDriver().connect(new ConnectSpec(address, port, PLAYER, JOIN_TIMEOUT));
        Await.until(PLAYER + " online per the agent", this::playerStatus, status -> status == 200, JOIN_TIMEOUT);
        // Chunks load around players only (docs/platforms/paper.md), and a server before 1.13 has no forceload, so
        // the block under the reset spot is placed once Alex is in. The golden world cases read it back.
        host.command("setblock 0 63 0 minecraft:stone");
        Await.until("stone under the spawn", this::spawnBlockBody, body -> body.contains("minecraft:stone"),
                JOIN_TIMEOUT);
        Reply reset = send("POST", "/v1/players/" + PLAYER + "/reset", true, null);
        assertThat(reset.status()).as("reset " + PLAYER + "; body was " + reset.body()).isEqualTo(200);
    }

    private String spawnBlockBody() {
        try {
            return send("GET", "/v1/world/block?world=world&x=0&y=63&z=0", true, null).body();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while reading the spawn block", e);
        }
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
