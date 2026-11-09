package io.github.h7kz.proofstone.agent.bungee;

import static org.assertj.core.api.Assertions.assertThat;

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
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.ServerConnectEvent;
import net.md_5.bungee.api.event.ServerSwitchEvent;
import net.md_5.bungee.api.plugin.Command;
import net.md_5.bungee.event.EventHandler;
import net.md_5.bungee.event.EventPriority;
import org.junit.jupiter.api.Test;

/**
 * Proves the observe-only rule (AGENTS.md section 3, ADR-0016 section 10) for BungeeCord. On BungeeCord a handler with a
 * HIGHER priority number runs LATER and has the last word, the reverse of Bukkit's names: {@code EventPriority.HIGHEST}
 * (64) is the last to run, the counterpart of Bukkit's MONITOR and Velocity's {@code PostOrder.LAST}, and {@code LOWEST}
 * (-64) is the first. The agent must watch what the proxy finally decided, and never decide, so every handler it
 * registers is at HIGHEST and none sets a result. Three kinds of evidence, because each alone has a gap: the real event
 * bus' registrations read back by reflection, a run through the real dispatch showing an event comes out unchanged, and a
 * scan of the compiled classes for the API that changes proxy state.
 */
class ObserveOnlyTest {

    /** Names of BungeeCord API members that change proxy state, cancel or alter an event, or reach a player. None may appear. */
    private static final List<String> FORBIDDEN = List.of(
            "setCancelled", "setTarget", "setCancelReason", "setCancelServer", "setKickReasonComponent", "setMessage",
            "registerCommand", "unregisterCommand", "unregisterCommands", "registerIntent", "completeIntent",
            "setReconnectServer", "setReconnectHandler", "setConfigurationAdapter", "registerChannel",
            "unregisterChannel", "setDisplayName", "sendData", "broadcast", "createTitle", "sendTitle", "showDialog",
            "setTabHeader", "setUniqueId", "setOnlineMode", 
            "getConsole", "setInstance");

    @Test
    void theAgentRegistersNoCommandAndEveryHandlerIsTheLastToRun() throws IOException {
        MockProxy mock = new MockProxy().server("lobby", 25565, true);
        mock.player("Alex", UUID.fromString("8a2c1f0e-6b1d-4f7e-9c3a-2d5e7b9a1c40"), "lobby");
        List<String> commandsBefore = commandNames(mock);
        List<Object> listenersBefore = mock.listeners();
        try (BungeeAgent agent = BungeeAgent.start(mock.host, "0.1.0",
                Map.of("PROOFSTONE_AGENT_TOKEN", AgentClient.TOKEN, "PROOFSTONE_AGENT_PORT", "0"))) {
            AgentClient http = new AgentClient(agent.port());
            // Touch every endpoint a proxy agent serves, a command and a probe included.
            http.get("/v1/health");
            http.get("/v1/players");
            http.get("/v1/players/Alex");
            http.get("/v1/proxy/servers");
            http.get("/v1/proxy/servers/lobby");
            http.get("/v1/log?since=0&minLevel=INFO");
            http.post("/v1/command", "{\"command\":\"version\"}");
            String probe = http.post("/v1/events/subscribe", "{\"class\":\"" + ServerConnectEvent.class.getName() + "\"}")
                    .json().get("data").get("probeId").asText();
            http.get("/v1/events/" + probe + "?since=0");

            assertThat(commandNames(mock)).as("the proxy's command registry").isEqualTo(commandsBefore);
            List<Object> agentListeners = new ArrayList<>(mock.listeners());
            agentListeners.removeAll(listenersBefore);
            assertThat(agentListeners).as("listeners the agent registered").isNotEmpty();
            int handlers = 0;
            for (Object listener : agentListeners) {
                for (Method method : listener.getClass().getDeclaredMethods()) {
                    EventHandler handler = method.getAnnotation(EventHandler.class);
                    if (handler != null) {
                        handlers++;
                        assertThat(handler.priority()).as(listener.getClass().getSimpleName() + "." + method.getName())
                                .isEqualTo(EventPriority.HIGHEST);
                        assertThat(method.getReturnType()).isEqualTo(void.class);
                    }
                }
            }
            assertThat(handlers).as("annotated handlers").isGreaterThan(5);
        }
    }

    @Test
    void anEventComesOutOfTheRealDispatchAsItWentIn() throws IOException {
        MockProxy mock = new MockProxy().server("lobby", 25565, true).server("survival", 25566, true);
        ProxiedPlayer alex = mock.player("Alex", UUID.fromString("8a2c1f0e-6b1d-4f7e-9c3a-2d5e7b9a1c40"), "lobby");
        try (BungeeAgent agent = BungeeAgent.start(mock.host, "0.1.0",
                Map.of("PROOFSTONE_AGENT_TOKEN", AgentClient.TOKEN, "PROOFSTONE_AGENT_PORT", "0"))) {
            AgentClient http = new AgentClient(agent.port());
            http.post("/v1/events/subscribe", "{\"class\":\"" + ServerConnectEvent.class.getName() + "\"}");
            http.post("/v1/events/subscribe", "{\"class\":\"" + ServerSwitchEvent.class.getName() + "\"}");

            ServerInfo lobby = mock.server("lobby");
            ServerConnectEvent connect = mock.plugins.callEvent(new ServerConnectEvent(alex, lobby));
            ServerSwitchEvent switched = mock.plugins.callEvent(new ServerSwitchEvent(alex, lobby));

            assertThat(connect.isCancelled()).isFalse();
            assertThat(connect.getTarget()).isSameAs(lobby);
            assertThat(switched.getFrom()).isSameAs(lobby);
        }
    }

    @Test
    void theHostCommandsAreStillThereAndRunnableAfterTheAgentStops() throws IOException {
        MockProxy mock = new MockProxy().server("lobby", 25565, true);
        List<String> commandsBefore = commandNames(mock);
        BungeeAgent agent = BungeeAgent.start(mock.host, "0.1.0",
                Map.of("PROOFSTONE_AGENT_TOKEN", AgentClient.TOKEN, "PROOFSTONE_AGENT_PORT", "0"));
        agent.close();
        assertThat(commandNames(mock)).isEqualTo(commandsBefore);
        assertThat(mock.bus.unregistered).as("every listener the agent registered is removed on close")
                .containsAll(mock.bus.registered.stream().filter(l -> l.getClass().getName().contains("proofstone"))
                        .collect(Collectors.toList()));
    }

    @Test
    void thePluginClassOverridesOnlyTheLifecycle() {
        List<String> declared = new ArrayList<>();
        for (Method method : ProofstoneAgentPlugin.class.getDeclaredMethods()) {
            declared.add(method.getName());
            assertThat(method.isAnnotationPresent(EventHandler.class)).as(method.getName()).isFalse();
        }
        assertThat(declared).containsExactlyInAnyOrder("onEnable", "onDisable");
    }

    @Test
    void noClassOfTheAgentNamesApiThatChangesProxyState() throws IOException, URISyntaxException {
        Path classes = new File(BungeeAgent.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toPath();
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

    @Test
    void anUnsupportedEventClassIsAnErrorNotASilentNoOp() throws IOException {
        MockProxy mock = new MockProxy();
        try (BungeeAgent agent = BungeeAgent.start(mock.host, "0.1.0",
                Map.of("PROOFSTONE_AGENT_TOKEN", AgentClient.TOKEN, "PROOFSTONE_AGENT_PORT", "0"))) {
            AgentClient.Reply reply = new AgentClient(agent.port())
                    .post("/v1/events/subscribe", "{\"class\":\"java.lang.String\"}");
            assertThat(reply.status).isEqualTo(400);
            assertThat(reply.errorCode()).isEqualTo("INVALID_REQUEST");
            assertThat(reply.json().get("error").get("message").asText()).contains("java.lang.String")
                    .contains(ServerSwitchEvent.class.getSimpleName());
        }
    }

    private static List<String> commandNames(MockProxy mock) {
        List<String> names = new ArrayList<>();
        for (Map.Entry<String, Command> entry : mock.plugins.getCommands()) {
            names.add(entry.getKey());
        }
        names.sort(String::compareTo);
        return names;
    }
}
