package io.github.h7kz.proofstone.agent.neoforge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.h7kz.proofstone.agent.protocol.AgentException;
import io.github.h7kz.proofstone.agent.vanilla.ClassScan;
import io.github.h7kz.proofstone.agent.vanilla.MinecraftView;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.BusBuilder;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import org.junit.jupiter.api.Test;

/**
 * Proves the observe-only rule (AGENTS.md section 3, ADR-0017 section 5) three ways, because each alone has a gap: the
 * mod descriptor declares no mixin, no access transformer and no dependency (so nothing about a client side) and the mod
 * loads on the dedicated server only; a probe listens at {@code LOWEST}, after every listener of a higher priority, sees
 * cancelled events too and leaves the cancelled state as the other listeners made it; and no compiled class names
 * NeoForge or game API that registers a command or a network payload, cancels an event, sets a result or mixes in.
 */
class ObserveOnlyTest {

    /** Names of API that registers commands or payloads, mixes into the game, or decides an event. None may appear. */
    private static final List<String> FORBIDDEN = List.of(
            "RegisterCommandsEvent", "setCanceled", "setResult", "org/spongepowered/asm/mixin", "Mixin",
            "RegisterPayloadHandlersEvent", "PayloadRegistrar", "net/neoforged/neoforge/network", "DeferredRegister",
            "EventBusSubscriber", "SubscribeEvent");

    @Test
    void theDescriptorDeclaresOneServerOnlyModWithNoMixinAndNoDependency() throws IOException {
        String toml;
        try (InputStream in = ObserveOnlyTest.class.getResourceAsStream("/META-INF/neoforge.mods.toml")) {
            assertThat(in).as("META-INF/neoforge.mods.toml").isNotNull();
            toml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        List<String> lines = toml.lines().map(String::strip)
                .filter(line -> !line.isEmpty() && !line.startsWith("#")).collect(Collectors.toList());
        assertThat(lines).contains("modLoader = \"javafml\"", "[[mods]]",
                "modId = \"" + ProofstoneNeoForgeAgent.MOD_ID + "\"");
        assertThat(lines.stream().filter("[[mods]]"::equals)).hasSize(1);
        // No [[dependencies...]] (a side= there is how a mod asks for a client counterpart), no mixins, no AT.
        assertThat(lines).noneMatch(line -> line.startsWith("[[dependencies"))
                .noneMatch(line -> line.startsWith("[[mixins"))
                .noneMatch(line -> line.startsWith("[[accessTransformers"))
                .noneMatch(line -> line.contains("side"));
        Mod mod = ProofstoneNeoForgeAgent.class.getAnnotation(Mod.class);
        assertThat(mod.value()).isEqualTo(ProofstoneNeoForgeAgent.MOD_ID);
        assertThat(mod.dist()).containsExactly(Dist.DEDICATED_SERVER);
    }

    @Test
    void aProbeRunsAfterEveryHigherPriorityListener() {
        IEventBus bus = BusBuilder.builder().build();
        NeoForgeEventProbes probes = new NeoForgeEventProbes(bus, () -> 7L);
        String probe = probes.subscribe(SampleEvents.Ordered.class.getName());
        // Registered after the probe, at every priority above LOWEST: each must still run before it.
        List<String> seen = new ArrayList<>();
        for (EventPriority priority : List.of(EventPriority.HIGHEST, EventPriority.HIGH, EventPriority.NORMAL,
                EventPriority.LOW)) {
            bus.addListener(priority, SampleEvents.Ordered.class,
                    event -> seen.add(priority + "=" + ((List<?>) probes.events(probe, 0).get("events")).size()));
        }
        bus.post(new SampleEvents.Ordered());
        assertThat(seen).containsExactly("HIGHEST=0", "HIGH=0", "NORMAL=0", "LOW=0");
        assertThat((List<?>) probes.events(probe, 0).get("events")).hasSize(1);
    }

    @Test
    void aProbeSeesCancelledEventsAndLeavesTheCancelledStateAsItWas() {
        IEventBus bus = BusBuilder.builder().build();
        NeoForgeEventProbes probes = new NeoForgeEventProbes(bus, () -> 0L);
        String probe = probes.subscribe(SampleEvents.Cancellable.class.getName());
        SampleEvents.Cancellable cancelled = new SampleEvents.Cancellable();
        cancelled.setCanceled(true);
        assertThat(bus.post(cancelled).isCanceled()).isTrue();
        assertThat(bus.post(new SampleEvents.Cancellable()).isCanceled()).isFalse();
        assertThat((List<?>) probes.events(probe, 0).get("events")).as("both posts were recorded").hasSize(2);
    }

    @Test
    void anUnsubscribedProbeLeavesTheBus() {
        IEventBus bus = BusBuilder.builder().build();
        NeoForgeEventProbes probes = new NeoForgeEventProbes(bus, () -> 0L);
        String first = probes.subscribe(SampleEvents.Joined.class.getName());
        String second = probes.subscribe(SampleEvents.Joined.class.getName());
        probes.unsubscribe(first);
        bus.post(new SampleEvents.Joined("Alex", 1));
        assertThat((List<?>) probes.events(second, 0).get("events")).hasSize(1);
        assertThatThrownBy(() -> probes.events(first, 0)).isInstanceOfSatisfying(AgentException.class,
                e -> assertThat(e.code()).isEqualTo("PROBE_NOT_FOUND"));
    }

    @Test
    void unknownNamesNonEventsAndAbstractEventsAreErrorsNotSilentProbes() {
        NeoForgeEventProbes probes = new NeoForgeEventProbes(BusBuilder.builder().build(), () -> 0L);
        for (String name : List.of("no.such.Type", SampleEvents.NotAnEvent.class.getName(),
                SampleEvents.Family.class.getName(), "")) {
            assertThatThrownBy(() -> probes.subscribe(name)).as(name)
                    .isInstanceOfSatisfying(AgentException.class, e -> assertThat(e.code()).isEqualTo("INVALID_REQUEST"));
        }
    }

    @Test
    void noClassOfTheAgentNamesApiThatChangesTheServer() throws IOException, URISyntaxException {
        Map<String, String> agent = ClassScan.classesOf(NeoForgeAgent.class);
        // T-M8-15: the vanilla game-view code is shared with the other loader's agent and shaded into this jar. It is its
        // own module, so the scan reaches it through its own class location: without that a change there would slip past.
        Map<String, String> shared = ClassScan.classesOf(MinecraftView.class);
        assertThat(agent.keySet()).as("compiled agent classes").isNotEmpty().noneMatch(name -> name.contains("/vanilla/"));
        assertThat(shared.keySet()).as("the scan must cover the shared vanilla-view classes")
                .anyMatch(name -> name.endsWith("/agent/vanilla/MinecraftView.class"))
                .anyMatch(name -> name.endsWith("/agent/vanilla/Values.class"))
                .allMatch(name -> name.contains("/agent/vanilla/"));
        Map<String, String> all = new TreeMap<>(agent);
        all.putAll(shared);
        all.forEach((name, content) -> {
            for (String forbidden : FORBIDDEN) {
                assertThat(content).as(name + " must not refer to " + forbidden).doesNotContain(forbidden);
            }
        });
    }
}
