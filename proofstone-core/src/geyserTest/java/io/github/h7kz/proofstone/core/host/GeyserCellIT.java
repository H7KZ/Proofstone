package io.github.h7kz.proofstone.core.host;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.dockerjava.api.model.Container;
import io.github.h7kz.proofstone.core.cell.CellPlanner;
import io.github.h7kz.proofstone.core.cell.GeyserCell;
import io.github.h7kz.proofstone.core.cell.Scope;
import io.github.h7kz.proofstone.core.driver.ClientSession;
import io.github.h7kz.proofstone.core.driver.ConnectSpec;
import io.github.h7kz.proofstone.core.driver.JoinTimeoutException;
import io.github.h7kz.proofstone.core.host.standin.StandIn;
import io.github.h7kz.proofstone.core.junit.ErrorLogGuard;
import io.github.h7kz.proofstone.core.junit.GeyserBundle;
import io.github.h7kz.proofstone.core.versions.VersionsCatalog;
import io.github.h7kz.proofstone.protocol.LogPage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;

/**
 * T-M9-06 on a real Docker: boots {@code geyser@2.12.1+paper@26.3} (Paper, Geyser, Floodgate, the Bukkit agent, the
 * discovery stand-in and the Bedrock driver host), reads health, joins one Bedrock client through the driver host and
 * reads it back through the agent under the Floodgate name. One cell per invocation (M9 flake rule). Needs the EULA in
 * the command environment; without it the test is skipped with that reason, never passed.
 */
class GeyserCellIT {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String NAME = "PsBed1";

    private static String property(String name) {
        String value = System.getProperty(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("system property " + name + " is not set (run through :proofstone-core:geyserTest)");
        }
        return value;
    }

    private static GeyserCell cell() {
        Path root = Path.of(property("proofstone.repoRoot"));
        VersionsCatalog catalog = VersionsCatalog.load(root.resolve("versions.yml"),
                root.resolve("proofstone-protocol/schemas/versions.schema.json"));
        return new CellPlanner(catalog).planGeysers(List.of("2.12.1"), "paper", List.of("26.3"), Scope.NIGHTLY).get(0);
    }

    @Test
    void bootsTheGeyserRowReadsHealthAndJoinsOneBedrockClientThroughTheDriverHost() throws Exception {
        assumeTrue(Eula.accepted(), "Minecraft EULA not accepted: set " + Eula.VARIABLE + "=true");
        GeyserCell cell = cell();
        PaperHostOptions server = PaperHostOptions.of(Path.of(property("proofstone.agentJar")), true);
        GeyserHostOptions options = GeyserHostOptions.of(Path.of(property("proofstone.bedrockHostJar")))
                .withDownloads(Path.of(property("proofstone.repoRoot"), "build", "geyser-downloads"));

        long started = System.nanoTime();
        try (GeyserHost host = GeyserHost.start(cell, server, options)) {
            System.out.println("GEYSER-CELL ready in " + Duration.ofNanos(System.nanoTime() - started).toMillis() + " ms: " + host);
            JsonNode health = host.server().request("GET", "/v1/health", null);
            System.out.println("GEYSER-CELL health " + health);
            assertThat(JSON.convertValue(health.path("capabilities"), List.class)).contains("floodgate");

            long join = System.nanoTime();
            ClientSession session;
            try {
                session = host.driver().connect(new ConnectSpec(host.bedrockHost(), host.bedrockPort(), NAME,
                        Duration.ofSeconds(30)));
            } catch (JoinTimeoutException e) {
                // Bedrock join timeouts are counted apart from Paper and topology numbers, with the stalled stage named.
                System.out.println("BEDROCK-JOIN-TIMEOUT stage=" + e.stage() + " " + e.getMessage());
                System.out.println("--- server log tail ---\n" + host.server().logTail());
                System.out.println("--- stand-in log ---\n" + host.standInLogs().all());
                System.out.println("--- driver host log tail ---\n" + host.driverHostLogs().tail(60));
                throw e;
            }
            List<String> transcript;
            try (session) {
                System.out.println("BEDROCK-JOIN spawned in " + Duration.ofNanos(System.nanoTime() - join).toMillis() + " ms");
                String floodgateName = "." + NAME;
                JsonNode players = host.server().request("GET", "/v1/players", null);
                System.out.println("GEYSER-CELL players " + players);
                assertThat(players.toString()).contains(floodgateName);
                JsonNode state = host.server().request("GET", "/v1/players/" + floodgateName, null);
                System.out.println("GEYSER-CELL player state " + state);
                assertThat(state.path("name").asText(floodgateName)).isEqualTo(floodgateName);
                double[] position = session.position();
                System.out.println("GEYSER-CELL client position " + Arrays.toString(position));
                System.out.println("GEYSER-CELL client inventory " + session.inventory());
                transcript = session.transcript();
            }

            // The stand-in saw exactly the two Microsoft names and nothing the guard refuses.
            System.out.println("STAND-IN names asked: " + host.namesAsked());
            System.out.println("--- stand-in log ---\n" + host.standInLogs().all());
            assertThat(host.namesAnswered()).isEqualTo(StandIn.NAMES);
            host.requireNameGuardClean();

            // The server's WARN and worse, for the log-guard list: from the harness's log buffer and from the agent.
            String warns = String.join("\n", Arrays.stream(host.server().logs().all().split("\\R"))
                    .filter(line -> line.contains("WARN") || line.contains("ERROR") || line.contains("SEVERE")).toList());
            System.out.println("--- server WARN/ERROR lines ---\n" + warns);
            LogPage page = JSON.treeToValue(host.server().request("GET", "/v1/log?since=0&minLevel=WARN", null), LogPage.class);
            System.out.println("--- agent WARN records ---");
            page.records().forEach(r -> System.out.println(r.level() + " " + r.logger() + ": " + r.message()));
            // Boot and join together: every WARN is on the Geyser list, or is Paper's offline-mode notice (ONLINE_MODE=FALSE in every cell).
            var checked = page.records().stream().filter(r -> !r.logger().endsWith("DedicatedServer")).toList();
            assertThat(ErrorLogGuard.describe(checked, ErrorLogGuard.Patterns.of("geyser"))).as("log guard, geyser list").isNull();
            assertThat(page.records().stream().filter(r -> r.level().equals("ERROR") || r.level().equals("SEVERE")).toList())
                    .as("ERROR records in the agent log").isEmpty();

            // The bundle holds no key in any form (the unit test greps the same on made-up files; this is the real cell).
            Path bundle = GeyserBundle.write(Path.of(property("proofstone.repoRoot"), "build", "geyser-bundle"), cell, host,
                    transcript);
            System.out.println("GEYSER-CELL bundle " + bundle);
            try (var files = Files.walk(bundle)) {
                for (Path file : files.filter(Files::isRegularFile).toList()) {
                    String text = Files.readString(file);
                    for (String form : host.floodgateKeyForms()) {
                        assertThat(text).as("bundle file " + file + " holds a form of the Floodgate key").doesNotContain(form);
                    }
                    assertThat(text).doesNotContain(host.server().agentToken()).doesNotContain(host.driverToken());
                }
            }
        }
        assertThat(ourRunningContainers()).as("containers of ours still running").isEmpty();
    }

    /** The containers of this test JVM's Testcontainers session that still run, except its Ryuk reaper. */
    private static List<String> ourRunningContainers() {
        List<Container> running = DockerClientFactory.instance().client().listContainersCmd()
                .withLabelFilter(Map.of("org.testcontainers.sessionId", DockerClientFactory.SESSION_ID)).exec();
        return running.stream().filter(c -> !c.getImage().contains("ryuk"))
                .map(c -> Arrays.toString(c.getNames()) + " " + c.getImage()).toList();
    }
}
