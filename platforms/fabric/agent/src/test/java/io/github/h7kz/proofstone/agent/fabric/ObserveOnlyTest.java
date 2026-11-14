package io.github.h7kz.proofstone.agent.fabric;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.h7kz.proofstone.agent.protocol.AgentException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Proves the observe-only rule (AGENTS.md section 3, ADR-0017 section 5) three ways, because each alone has a gap: the
 * mod descriptor declares no mixin and no client side; a probe runs after every default-phase listener, refuses a
 * callback that decides a result, and leaves the result as the other listeners made it; and no compiled class names
 * Fabric or game API that registers a command, cancels an event or changes behaviour.
 */
class ObserveOnlyTest {

    /** Names of API that registers commands, mixes into the game, or decides an event. None may appear. */
    private static final List<String> FORBIDDEN = List.of(
            "CommandRegistrationCallback", "org/spongepowered/asm/mixin", "Mixin", "ServerMessageEvents",
            "ALLOW_", "InteractionResult", "AttackBlockCallback", "UseBlockCallback", "UseItemCallback",
            "PlayerBlockBreakEvents", "ServerPlayNetworking", "PayloadTypeRegistry");

    @Test
    void theDescriptorDeclaresOnlyAServerEntrypointAndNoMixin() throws IOException {
        JsonNode mod;
        try (InputStream in = ObserveOnlyTest.class.getResourceAsStream("/fabric.mod.json")) {
            mod = AgentClient.JSON.readTree(in);
        }
        assertThat(mod.get("id").asText()).isEqualTo(ProofstoneFabricAgent.MOD_ID);
        assertThat(mod.get("environment").asText()).isEqualTo("server");
        assertThat(mod.has("mixins")).as("mixins").isFalse();
        assertThat(mod.has("accessWidener")).as("access widener").isFalse();
        List<String> stages = new ArrayList<>();
        mod.get("entrypoints").fieldNames().forEachRemaining(stages::add);
        assertThat(stages).containsExactly("server");
        assertThat(mod.get("entrypoints").get("server").get(0).asText())
                .isEqualTo(ProofstoneFabricAgent.class.getName());
        assertThat(mod.get("depends").has("fabric-lifecycle-events-v1")).isTrue();
    }

    @Test
    void aProbeRunsAfterEveryDefaultListenerAndLeavesTheEventAsItWas() {
        FabricEventProbes probes = new FabricEventProbes(() -> 7L);
        String probe = probes.subscribe(SampleEvents.class.getName() + ".ORDERED");
        // Registered after the probe, in the default phase: it must still run before it.
        List<Integer> seenByDefault = new ArrayList<>();
        SampleEvents.ORDERED.register((name, count) ->
                seenByDefault.add(((List<?>) probes.events(probe, 0).get("events")).size()));
        SampleEvents.ORDERED.invoker().onJoin("Alex", 1);
        assertThat(seenByDefault).containsExactly(0);
        assertThat((List<?>) probes.events(probe, 0).get("events")).hasSize(1);
    }

    @Test
    void aCallbackThatDecidesAResultCannotBeProbed() {
        FabricEventProbes probes = new FabricEventProbes(() -> 0L);
        assertThatThrownBy(() -> probes.subscribe(SampleEvents.class.getName() + ".ALLOW"))
                .isInstanceOf(AgentException.class)
                .hasMessageContaining("observe only");
        // Nothing was registered: a vote of the other listeners is the result.
        SampleEvents.ALLOW.register(name -> false);
        assertThat(SampleEvents.ALLOW.invoker().allow("Alex")).isFalse();
    }

    @Test
    void unknownNamesAndNonEventsAreErrorsNotSilentProbes() {
        FabricEventProbes probes = new FabricEventProbes(() -> 0L);
        for (String name : List.of("no.such.Type.FIELD", SampleEvents.class.getName() + ".MISSING",
                SampleEvents.class.getName() + ".NOT_AN_EVENT", "NoDot")) {
            assertThatThrownBy(() -> probes.subscribe(name)).as(name)
                    .isInstanceOfSatisfying(AgentException.class, e -> assertThat(e.code()).isEqualTo("INVALID_REQUEST"));
        }
    }

    @Test
    void noClassOfTheAgentNamesApiThatChangesTheServer() throws IOException, URISyntaxException {
        Path classes = new File(FabricAgent.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toPath();
        List<Path> files;
        try (Stream<Path> walk = Files.walk(classes)) {
            files = walk.filter(path -> path.toString().endsWith(".class")).collect(Collectors.toList());
        }
        assertThat(files).as("compiled agent classes under " + classes).isNotEmpty();
        for (Path file : files) {
            // Constant-pool names are UTF-8, so a byte scan finds every method, class and field the code refers to.
            String content = new String(Files.readAllBytes(file), StandardCharsets.ISO_8859_1);
            for (String name : FORBIDDEN) {
                assertThat(content).as(file.getFileName() + " must not refer to " + name).doesNotContain(name);
            }
        }
    }
}
