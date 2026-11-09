package io.github.h7kz.proofstone.agent.velocity;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.proxy.Player;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.helpers.NOPLogger;

/**
 * The events endpoints on a mock proxy. They are implemented and unit-tested here, but the {@code events} capability is
 * withheld until a real Velocity has run them (T-M7-11), so these tests do not claim it.
 */
class EventProbesTest {

    private static final String SAMPLE = SampleEvent.class.getName();

    private MockProxy mock;
    private VelocityAgent agent;
    private AgentClient http;

    @BeforeEach
    void start() throws IOException {
        mock = new MockProxy().server("lobby", 25565, true).server("survival", 25566, true);
        agent = VelocityAgent.start(mock.proxy, new Object(), NOPLogger.NOP_LOGGER, "0.1.0",
                Map.of("PROOFSTONE_AGENT_TOKEN", AgentClient.TOKEN, "PROOFSTONE_AGENT_PORT", "0"));
        http = new AgentClient(agent.port());
    }

    @AfterEach
    void stop() {
        agent.close();
    }

    private String subscribe() throws IOException {
        AgentClient.Reply reply = http.post("/v1/events/subscribe", "{\"class\":\"" + SAMPLE + "\"}");
        assertThat(reply.status).isEqualTo(200);
        return reply.json().get("data").get("probeId").asText();
    }

    @Test
    void aProbeRegistersAtTheLastPriority() throws IOException {
        subscribe();
        MockProxy.Registration probe = mock.registrations.stream().filter(r -> r.type == SampleEvent.class)
                .findFirst().orElseThrow();
        assertThat(probe.order).isEqualTo(PostOrder.LAST);
    }

    @Test
    void aProbeRecordsTheGettersAndNeverCallsASetter() throws IOException {
        String probe = subscribe();
        Player alex = mock.player("Alex", UUID.fromString("8a2c1f0e-6b1d-4f7e-9c3a-2d5e7b9a1c40"), "lobby");
        SampleEvent event = new SampleEvent(alex, mock.server("lobby"), mock.server("survival"));

        mock.handlerFor(SampleEvent.class).execute(event);

        assertThat(event.setterCalls).isZero();
        assertThat(event.chosen()).isSameAs(mock.server("survival"));
        JsonNode page = http.get("/v1/events/" + probe + "?since=0").json().get("data");
        assertThat(page.get("next").asInt()).isEqualTo(1);
        JsonNode captured = page.get("events").get(0);
        assertThat(captured.get("class").asText()).isEqualTo(SAMPLE);
        assertThat(captured.get("tick").asInt()).isZero();
        assertThat(captured.get("thread").asText()).isNotEmpty();
        JsonNode fields = captured.get("fields");
        assertThat(fields.get("getPlayer").get("name").asText()).isEqualTo("Alex");
        assertThat(fields.get("getServer").get("name").asText()).isEqualTo("lobby");
        assertThat(fields.get("getInitialServer").get("name").asText()).isEqualTo("survival");
        assertThat(fields.get("getNothing").isNull()).isTrue();
        assertThat(fields.get("getReason").asText()).isEqualTo("because");
        assertThat(fields.get("getCount").asInt()).isEqualTo(3);
        assertThat(fields.get("getFlag").asBoolean()).isTrue();
        assertThat(fields.get("getId").get("$type").asText()).isEqualTo("java.util.UUID");
        assertThat(fields.get("getBroken").get("$type").asText()).isEqualTo("error");
        assertThat(fields.has("setInitialServer")).isFalse();
    }

    @Test
    void theCursorPagesTheEvents() throws IOException {
        String probe = subscribe();
        Player alex = mock.player("Alex", UUID.fromString("8a2c1f0e-6b1d-4f7e-9c3a-2d5e7b9a1c40"), "lobby");
        for (int i = 0; i < 2; i++) {
            mock.handlerFor(SampleEvent.class).execute(new SampleEvent(alex, mock.server("lobby"), null));
        }
        assertThat(http.get("/v1/events/" + probe + "?since=1").json().get("data").get("events")).hasSize(1);
        assertThat(http.get("/v1/events/" + probe + "?since=2").json().get("data").get("events")).isEmpty();
    }

    @Test
    void unsubscribeRemovesTheHandler() throws IOException {
        String probe = subscribe();
        AgentClient.Reply gone = http.send("DELETE", "/v1/events/" + probe, "Bearer " + AgentClient.TOKEN, null);
        assertThat(gone.status).isEqualTo(200);
        assertThat(mock.unregistered).hasSize(1);
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
