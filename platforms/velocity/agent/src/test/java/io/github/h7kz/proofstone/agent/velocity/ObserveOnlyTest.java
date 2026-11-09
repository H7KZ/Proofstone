package io.github.h7kz.proofstone.agent.velocity;

import static org.assertj.core.api.Assertions.assertThat;

import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.proxy.Player;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.slf4j.helpers.NOPLogger;

/**
 * Proves the observe-only rule (AGENTS.md section 3, ADR-0016 section 10): the agent registers no command, has no
 * handler below the last priority, calls no mutator, and a proxy event comes out of a probe as it went in. Two kinds of
 * evidence, because each alone has a gap: a run against a mock that fails on a command registration, and a scan of the
 * compiled classes for the API that changes proxy state.
 */
class ObserveOnlyTest {

    /** Names of Velocity API members that change proxy state, cancel an event or reach a player. None may appear. */
    private static final List<String> FORBIDDEN = List.of(
            "CommandMeta", "BrigadierCommand", "metaBuilder", "setResult", "setInitialServer", "createConnectionRequest",
            "registerServer", "unregisterServer", "createRawRegisteredServer", "spoofChatInput", "sendPluginMessage",
            "transferToHost", "disconnect", "closeListeners", "shutdown", "setEffectiveLocale", "sendResourcePack",
            "setGameProfileProperties", "getTabList", "DeniedResult", "ComponentResult");

    @Test
    void theAgentRegistersNoCommandAndEveryHandlerIsLast() throws IOException {
        MockProxy mock = new MockProxy().server("lobby", 25565, true);
        Player alex = mock.player("Alex", UUID.fromString("8a2c1f0e-6b1d-4f7e-9c3a-2d5e7b9a1c40"), "lobby");
        try (VelocityAgent agent = VelocityAgent.start(mock.proxy, new Object(), NOPLogger.NOP_LOGGER, "0.1.0",
                Map.of("PROOFSTONE_AGENT_TOKEN", AgentClient.TOKEN, "PROOFSTONE_AGENT_PORT", "0"))) {
            AgentClient http = new AgentClient(agent.port());
            // Touch every endpoint a proxy agent serves, a command and a probe included.
            http.get("/v1/health");
            http.get("/v1/players");
            http.get("/v1/players/Alex");
            http.get("/v1/proxy/servers");
            http.get("/v1/proxy/servers/lobby");
            http.get("/v1/log?since=0&minLevel=INFO");
            http.post("/v1/command", "{\"command\":\"glist\"}");
            String probe = http.post("/v1/events/subscribe", "{\"class\":\"" + SampleEvent.class.getName() + "\"}")
                    .json().get("data").get("probeId").asText();
            http.get("/v1/events/" + probe + "?since=0");

            assertThat(mock.commandRegistrations).as("command registry calls").isEmpty();
            assertThat(mock.registrations).as("event handlers").isNotEmpty();
            assertThat(mock.registrations).allSatisfy(registration ->
                    assertThat(registration.order).as(registration.type.getSimpleName()).isEqualTo(PostOrder.LAST));

            // A probe leaves the event exactly as it found it.
            SampleEvent event = new SampleEvent(alex, mock.server("lobby"), mock.server("lobby"));
            mock.handlerFor(SampleEvent.class).execute(event);
            assertThat(event.setterCalls).isZero();
            assertThat(event.chosen()).isSameAs(mock.server("lobby"));
        }
    }

    @Test
    void thePluginClassHandlesItsOwnEventsAtTheLastPriorityOnly() {
        List<Method> handlers = new ArrayList<>();
        for (Method method : ProofstoneAgentPlugin.class.getDeclaredMethods()) {
            if (method.isAnnotationPresent(Subscribe.class)) {
                handlers.add(method);
            }
        }
        assertThat(handlers).isNotEmpty();
        assertThat(handlers).allSatisfy(method ->
                assertThat(method.getAnnotation(Subscribe.class).order()).as(method.getName()).isEqualTo(PostOrder.LAST));
        assertThat(handlers).allSatisfy(method -> assertThat(method.getReturnType()).isEqualTo(void.class));
    }

    @Test
    void noClassOfTheAgentNamesApiThatChangesProxyState() throws IOException, URISyntaxException {
        Path classes = new File(VelocityAgent.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toPath();
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
