package io.github.h7kz.proofstone.agent.fabric;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.dockerjava.api.model.Container;
import io.github.h7kz.proofstone.core.cell.CellPlanner;
import io.github.h7kz.proofstone.core.cell.LoaderCell;
import io.github.h7kz.proofstone.core.cell.Scope;
import io.github.h7kz.proofstone.core.host.Eula;
import io.github.h7kz.proofstone.core.host.LoaderHost;
import io.github.h7kz.proofstone.core.host.LoaderHostOptions;
import io.github.h7kz.proofstone.core.host.PaperHostException;
import io.github.h7kz.proofstone.core.versions.VersionsCatalog;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;

/**
 * The loader host on the fabric@26.3 row (T-M8-07): {@link LoaderHost} boots the row with the shaded agent, the pinned
 * Fabric API and the example mod under test, and reads {@code health}. One cell per invocation (M8 flake rule): run it
 * with {@code --tests '*FabricLoaderHostIT.bootsTheFabricRow*'} under {@code scripts/dockerlock.sh}, three separate
 * invocations. The second test is the failed start, run once per row.
 *
 * <p>The pins are downloaded by the host, checked by sha256 and copied in (ADR-0005, ADR-0017 section 3); the install
 * reaches Mojang's servers at a cold start, the listed exception of ADR-0017 section 6.
 */
class FabricLoaderHostIT {

    private static final Path ROOT = Path.of(System.getProperty("proofstone.repoRoot"));
    private static final Path AGENT_JAR = Path.of(System.getProperty("proofstone.agentJar"));
    private static final Path EXAMPLE_MOD = Path.of(System.getProperty("proofstone.exampleMod"));
    private static final Path CACHE = AGENT_JAR.getParent().getParent().resolve("loader-it/cache");
    private static final String CONFIG = "/config/proofstone-example.properties";

    private static LoaderCell plannedCell() {
        VersionsCatalog catalog = VersionsCatalog.load(ROOT.resolve("versions.yml"),
                ROOT.resolve("proofstone-protocol/schemas/versions.schema.json"));
        return new CellPlanner(catalog).planLoaders("fabric", List.of("26.3"), Scope.NIGHTLY).get(0);
    }

    private static LoaderHostOptions options(String exampleConfig) {
        return LoaderHostOptions.of(AGENT_JAR, Eula.accepted(), CACHE)
                .withMods(EXAMPLE_MOD.getParent(), List.of(EXAMPLE_MOD.getFileName().toString()))
                .withFiles(Map.of(CONFIG, exampleConfig));
    }

    @Test
    void bootsTheFabricRowWithTheAgentAndTheExampleModAndReadsHealth() {
        assumeTrue(Eula.accepted(), "Minecraft EULA not accepted: set " + Eula.VARIABLE + "=true");
        LoaderCell cell = plannedCell();
        long started = System.nanoTime();

        try (LoaderHost host = LoaderHost.start(cell, options("fail=false\n"))) {
            Duration total = Duration.ofNanos(System.nanoTime() - started);
            // The boot time is a host finding, never part of a join timeout (M8 flake rule 4).
            System.out.println("LOADER-HOST " + cell.identity() + " boot " + host.bootTime().toMillis() + " ms to Done, "
                    + total.toMillis() + " ms to ready (cold start, install included)");
            assertThat(host.health().platform()).isEqualTo("fabric");
            assertThat(host.health().mcVersion()).isEqualTo("26.3");
            assertThat(host.health().capabilities()).contains("mods");
            JsonNode mods = host.request("GET", "/v1/mods", null).get("mods");
            List<String> ids = new ArrayList<>();
            mods.forEach(mod -> ids.add(mod.get("id").asText()));
            assertThat(ids).contains("proofstone_example", "fabric-api", "proofstone-agent");
            assertThat(host.logs().contains("[proofstone-example] example mod loaded")).isTrue();
            // T-M8-27: the same deterministic-world rules PaperHost sets, so the baseline is not the vanilla default 3.
            for (String rule : List.of("random_tick_speed", "advance_time", "advance_weather", "spawn_mobs")) {
                System.out.println("LOADER-HOST " + cell.identity() + " " + host.command("gamerule " + rule));
            }
            assertThat(host.command("gamerule random_tick_speed")).contains("set to 0");
            assertThat(host.command("gamerule advance_time")).contains("set to false");
            assertThat(host.command("gamerule advance_weather")).contains("set to false");
            assertThat(host.command("gamerule spawn_mobs")).contains("set to false");
        }
        assertThat(runningHostContainers()).as("containers of this run still running after close").isEmpty();
    }

    @Test
    void aFailedStartFailsAtOnceWithTheLogTailAndLeavesNoContainer() {
        assumeTrue(Eula.accepted(), "Minecraft EULA not accepted: set " + Eula.VARIABLE + "=true");
        LoaderCell cell = plannedCell();
        long started = System.nanoTime();

        Throwable failure = catchThrowable(() -> LoaderHost.start(cell, options("fail=true\n")));
        assertThat(failure).as("the start must fail").isNotNull();
        System.out.println("LOADER-HOST " + cell.identity() + " failure: " + failure.getMessage().lines().findFirst().orElse(""));
        assertThat(failure)
                .isInstanceOf(PaperHostException.class)
                // T-M8-11: a mod that throws at init is INVALID naming the mod, not a plain "failed to boot" (ModLoad).
                .hasMessageContaining("INVALID test declaration")
                .hasMessageContaining("mod proofstone_example (proofstone-example-mod.jar) crashed the server at load on fabric@26.3")
                .hasMessageContaining("Failed to start the minecraft server")
                .hasMessageContaining("--- log tail ---")
                .hasMessageContaining("example mod was asked to fail")
                .message().doesNotContain("BOOT TIMEOUT");

        Duration elapsed = Duration.ofNanos(System.nanoTime() - started);
        System.out.println("LOADER-HOST " + cell.identity() + " failed start reported after " + elapsed.toMillis()
                + " ms (boot timeout is " + LoaderHostOptions.BOOT_TIMEOUT + ")");
        assertThat(elapsed).as("fail-fast, not the boot timeout").isLessThan(LoaderHostOptions.BOOT_TIMEOUT.dividedBy(2));
        assertThat(runningHostContainers()).as("containers of this run still running after the failure").isEmpty();
    }

    /** Running containers this JVM's Testcontainers session started from the itzg image: the host's own. */
    private static List<String> runningHostContainers() {
        List<String> names = new ArrayList<>();
        for (Container container : DockerClientFactory.lazyClient().listContainersCmd()
                .withLabelFilter(Map.of("org.testcontainers.sessionId", DockerClientFactory.SESSION_ID)).exec()) {
            if (container.getImage().startsWith("itzg/minecraft-server")) {
                names.add(String.join(",", container.getNames()));
            }
        }
        return names;
    }
}
