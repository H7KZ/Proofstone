package io.github.h7kz.proofstone.agent.bungee;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.ChatEvent;
import net.md_5.bungee.api.event.PluginMessageEvent;
import net.md_5.bungee.api.event.ServerConnectEvent;
import net.md_5.bungee.api.event.ServerSwitchEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The events endpoints on a mock proxy with the real event bus. They are implemented and unit-tested here, but the
 * {@code events} capability is withheld until a real BungeeCord has run them (T-M7-11), so these tests do not claim it.
 */
class EventProbesTest {

    private static final String CONNECT = ServerConnectEvent.class.getName();

    private MockProxy mock;
    private BungeeAgent agent;
    private AgentClient http;
    private ProxiedPlayer alex;

    @BeforeEach
    void start() throws IOException {
        mock = new MockProxy().server("lobby", 25565, true).server("survival", 25566, true);
        alex = mock.player("Alex", UUID.fromString("8a2c1f0e-6b1d-4f7e-9c3a-2d5e7b9a1c40"), "lobby");
        agent = BungeeAgent.start(mock.host, "0.1.0",
                Map.of("PROOFSTONE_AGENT_TOKEN", AgentClient.TOKEN, "PROOFSTONE_AGENT_PORT", "0"));
        http = new AgentClient(agent.port());
    }

    @AfterEach
    void stop() {
        agent.close();
    }

    private String subscribe(String className) throws IOException {
        AgentClient.Reply reply = http.post("/v1/events/subscribe", "{\"class\":\"" + className + "\"}");
        assertThat(reply.status).isEqualTo(200);
        return reply.json().get("data").get("probeId").asText();
    }

    @Test
    void aProbeRecordsTheGettersOfARealEventDispatchedByTheRealBus() throws IOException {
        String probe = subscribe(CONNECT);
        mock.plugins.callEvent(new ServerConnectEvent(alex, mock.server("survival"),
                ServerConnectEvent.Reason.COMMAND));

        JsonNode page = http.get("/v1/events/" + probe + "?since=0").json().get("data");
        assertThat(page.get("next").asInt()).isEqualTo(1);
        JsonNode captured = page.get("events").get(0);
        assertThat(captured.get("class").asText()).isEqualTo(CONNECT);
        assertThat(captured.get("tick").asInt()).isZero();
        assertThat(captured.get("thread").asText()).isNotEmpty();
        JsonNode fields = captured.get("fields");
        assertThat(fields.get("getPlayer").get("name").asText()).isEqualTo("Alex");
        assertThat(fields.get("getTarget").get("name").asText()).isEqualTo("survival");
        assertThat(fields.get("getReason").get("value").asText()).isEqualTo("COMMAND");
        assertThat(fields.has("setTarget")).isFalse();
        assertThat(fields.has("isCancelled")).isFalse();
    }

    @Test
    void aSwitchEventShowsTheServerTheSwitchCameFrom() throws IOException {
        String probe = subscribe(ServerSwitchEvent.class.getName());
        mock.plugins.callEvent(new ServerSwitchEvent(alex, mock.server("lobby")));
        mock.plugins.callEvent(new ServerSwitchEvent(alex, null));

        JsonNode events = http.get("/v1/events/" + probe + "?since=0").json().get("data").get("events");
        assertThat(events.get(0).get("fields").get("getFrom").get("name").asText()).isEqualTo("lobby");
        assertThat(events.get(1).get("fields").get("getFrom").isNull()).isTrue();
    }

    @Test
    void aMessageEventShowsItsTagAndTheSizeOfItsDataNotTheBytes() throws IOException {
        String probe = subscribe(PluginMessageEvent.class.getName());
        mock.plugins.callEvent(new PluginMessageEvent(alex, alex, "BungeeCord", new byte[] {1, 2, 3}));

        JsonNode fields = http.get("/v1/events/" + probe + "?since=0").json().get("data").get("events").get(0)
                .get("fields");
        assertThat(fields.get("getTag").asText()).isEqualTo("BungeeCord");
        assertThat(fields.get("getData").get("$type").asText()).isEqualTo("byte[]");
        assertThat(fields.get("getData").get("value").asText()).isEqualTo("3 bytes");
        assertThat(fields.get("getSender").get("name").asText()).isEqualTo("Alex");
    }

    @Test
    void aChatEventShowsItsMessageAndBothEnds() throws IOException {
        String probe = subscribe(ChatEvent.class.getName());
        mock.plugins.callEvent(new ChatEvent(alex, alex, "hello"));
        JsonNode fields = http.get("/v1/events/" + probe + "?since=0").json().get("data").get("events").get(0)
                .get("fields");
        assertThat(fields.get("getMessage").asText()).isEqualTo("hello");
    }

    @Test
    void twoProbesOnTheSameClassBothSeeTheEventAndOnlyTheSubscribedOnes() throws IOException {
        String first = subscribe(CONNECT);
        String second = subscribe(CONNECT);
        mock.plugins.callEvent(new ServerConnectEvent(alex, mock.server("lobby")));
        assertThat(http.get("/v1/events/" + first + "?since=0").json().get("data").get("events")).hasSize(1);
        assertThat(http.get("/v1/events/" + second + "?since=0").json().get("data").get("events")).hasSize(1);
        mock.plugins.callEvent(new ServerSwitchEvent(alex, null));
        assertThat(http.get("/v1/events/" + first + "?since=0").json().get("data").get("events"))
                .as("a probe records only its own class").hasSize(1);
    }

    @Test
    void theCursorPagesTheEvents() throws IOException {
        String probe = subscribe(CONNECT);
        for (int i = 0; i < 2; i++) {
            mock.plugins.callEvent(new ServerConnectEvent(alex, mock.server("lobby")));
        }
        assertThat(http.get("/v1/events/" + probe + "?since=1").json().get("data").get("events")).hasSize(1);
        assertThat(http.get("/v1/events/" + probe + "?since=2").json().get("data").get("events")).isEmpty();
    }

    @Test
    void unsubscribeStopsRecordingAndTheProbeIsGone() throws IOException {
        String probe = subscribe(CONNECT);
        AgentClient.Reply gone = http.send("DELETE", "/v1/events/" + probe, "Bearer " + AgentClient.TOKEN, null);
        assertThat(gone.status).isEqualTo(200);
        mock.plugins.callEvent(new ServerConnectEvent(alex, mock.server("lobby")));
        AgentClient.Reply again = http.get("/v1/events/" + probe + "?since=0");
        assertThat(again.status).isEqualTo(404);
        assertThat(again.errorCode()).isEqualTo("PROBE_NOT_FOUND");
    }

    @Test
    void anUnknownClassIsAnError() throws IOException {
        AgentClient.Reply reply = http.post("/v1/events/subscribe", "{\"class\":\"no.such.Event\"}");
        assertThat(reply.status).isEqualTo(400);
        assertThat(reply.errorCode()).isEqualTo("INVALID_REQUEST");
    }
}
