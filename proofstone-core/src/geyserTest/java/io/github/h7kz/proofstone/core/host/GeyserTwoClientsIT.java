package io.github.h7kz.proofstone.core.host;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.h7kz.proofstone.core.await.Await;
import io.github.h7kz.proofstone.core.cell.CellPlanner;
import io.github.h7kz.proofstone.core.cell.GeyserCell;
import io.github.h7kz.proofstone.core.cell.Scope;
import io.github.h7kz.proofstone.core.driver.ClientSession;
import io.github.h7kz.proofstone.core.driver.ConnectSpec;
import io.github.h7kz.proofstone.core.driver.Edition;
import io.github.h7kz.proofstone.core.versions.VersionsCatalog;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * T-M10-07 below the public API: two Bedrock clients through ONE driver host on one Geyser cell (ADR-0018 Consequences,
 * geyser.md "Still unrun"). Each gets its own XUID from the driver, so Floodgate gives each its own UUID; a chat line from
 * one reaches the other (the public {@code Client} has no chat call, so this is where chat is asserted); both leave and the
 * agent forgets both. One cell per invocation (M9 flake rule). Needs the EULA in the command environment; without it the
 * test is skipped with that reason, never passed.
 */
class GeyserTwoClientsIT {

    private static final Duration WAIT = Duration.ofSeconds(40);

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

    private static boolean listed(GeyserHost host, String name) {
        JsonNode players = host.server().request("GET", "/v1/players", null).get("players");
        if (players == null || !players.isArray()) {
            throw new IllegalStateException("the agent's /v1/players reply has no players list: " + players);
        }
        for (JsonNode player : players) {
            if (name.equals(player.asText())) {
                return true;
            }
        }
        return false;
    }

    private static UUID uuidOf(GeyserHost host, String name) {
        JsonNode state = host.server().request("GET", "/v1/players/" + name, null);
        assertThat(state.path("name").asText()).isEqualTo(name);
        return UUID.fromString(state.path("uuid").asText());
    }

    @Test
    void twoBedrockClientsOnOneDriverHostHaveDistinctUuidsChatToEachOtherAndLeaveCleanly() {
        assumeTrue(Eula.accepted(), "Minecraft EULA not accepted: set " + Eula.VARIABLE + "=true");
        PaperHostOptions server = PaperHostOptions.of(Path.of(property("proofstone.agentJar")), true);
        GeyserHostOptions options = GeyserHostOptions.of(Path.of(property("proofstone.bedrockHostJar")))
                .withDownloads(Path.of(property("proofstone.repoRoot"), "build", "geyser-downloads"));

        try (GeyserHost host = GeyserHost.start(cell(), server, options)) {
            ClientSession alice = host.driver().connect(new ConnectSpec(host.bedrockHost(), host.bedrockPort(), "PsTwoA",
                    Duration.ofSeconds(30)), Edition.BEDROCK);
            ClientSession bob;
            try {
                bob = host.driver().connect(new ConnectSpec(host.bedrockHost(), host.bedrockPort(), "PsTwoB",
                        Duration.ofSeconds(30)), Edition.BEDROCK);
            } catch (RuntimeException e) {
                alice.close();
                throw e;
            }
            try {
                Await.until("the agent lists .PsTwoA and .PsTwoB", () -> listed(host, ".PsTwoA") && listed(host, ".PsTwoB"),
                        both -> both, WAIT);
                UUID a = uuidOf(host, ".PsTwoA");
                UUID b = uuidOf(host, ".PsTwoB");
                System.out.println("GEYSER-TWO uuids " + a + " " + b);
                assertThat(a).isNotEqualTo(b);
                // Floodgate's shape: the XUID in the low bits, never the nil UUID (geyser.md, the Node driver's collision).
                assertThat(a).isNotEqualTo(new UUID(0, 0));
                assertThat(b).isNotEqualTo(new UUID(0, 0));
                assertThat(a.getMostSignificantBits()).isZero();
                assertThat(b.getMostSignificantBits()).isZero();

                alice.chat("hello from PsTwoA");
                String heard = bob.awaitMessage(Pattern.compile("hello from PsTwoA"), WAIT).text();
                System.out.println("GEYSER-TWO PsTwoB heard: " + heard);
                assertThat(heard).contains(".PsTwoA").contains("hello from PsTwoA");
                bob.chat("hello from PsTwoB");
                assertThat(alice.awaitMessage(Pattern.compile("hello from PsTwoB"), WAIT).text()).contains(".PsTwoB");
            } finally {
                alice.close();
                bob.close();
            }
            // The server notices a disconnect after about 10 s: awaited on the agent, never slept.
            Await.until("the agent no longer lists .PsTwoA or .PsTwoB", () -> listed(host, ".PsTwoA") || listed(host, ".PsTwoB"),
                    any -> !any, WAIT);
            host.requireNameGuardClean();
        }
    }
}
