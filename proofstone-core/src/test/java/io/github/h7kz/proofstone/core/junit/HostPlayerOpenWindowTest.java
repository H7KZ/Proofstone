package io.github.h7kz.proofstone.core.junit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.h7kz.proofstone.api.PlayerState;
import io.github.h7kz.proofstone.api.ServerWindow;
import io.github.h7kz.proofstone.core.host.AgentEndpoint;
import io.github.h7kz.proofstone.core.host.LogBuffer;
import io.github.h7kz.proofstone.protocol.Health;
import java.util.List;
import org.junit.jupiter.api.Test;

/** {@code PlayerState.openWindow} over a fake agent that replies with canned player-state JSON (T-M11-02). */
class HostPlayerOpenWindowTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String HEAD = "{\"name\":\"Alex\",\"uuid\":\"069a79f4-44e9-4726-a5be-fca90e38aaf5\",\"level\":0,"
            + "\"exp\":0.0,\"gamemode\":\"survival\",\"health\":20.0,"
            + "\"location\":{\"world\":\"world\",\"x\":0.5,\"y\":64.0,\"z\":0.5,\"yaw\":0.0,\"pitch\":0.0},"
            + "\"inventory\":{},\"cursor\":null,";

    private static PlayerState read(String tail) throws Exception {
        JsonNode reply = JSON.readTree(HEAD + tail);
        AgentEndpoint agent = new AgentEndpoint() {
            @Override
            public JsonNode request(String method, String path, Object body) {
                assertThat(method + " " + path).isEqualTo("GET /v1/players/Alex");
                return reply;
            }

            @Override
            public String command(String command) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Health health() {
                throw new UnsupportedOperationException();
            }

            @Override
            public String agentToken() {
                throw new UnsupportedOperationException();
            }

            @Override
            public List<String> agentTranscript() {
                throw new UnsupportedOperationException();
            }

            @Override
            public LogBuffer logs() {
                throw new UnsupportedOperationException();
            }
        };
        return new HostPlayer(agent, "Alex").state();
    }

    @Test
    void aNullWindowIsEmpty() throws Exception {
        assertThat(read("\"openWindow\":null}").openWindow()).isEmpty();
    }

    @Test
    void aWindowKeepsTypeTitleAndSlotOrderWithEmptySlotsAsEmptyViews() throws Exception {
        PlayerState state = read("\"openWindow\":{\"type\":\"chest\",\"title\":\"\uE001 Menu\",\"slots\":["
                + "null,{\"type\":\"minecraft:book\",\"amount\":2,\"raw\":\"r\"},null]}}");
        ServerWindow window = state.openWindow().orElseThrow();
        assertThat(window.type()).isEqualTo("chest");
        assertThat(window.title()).isEqualTo(" Menu");
        assertThat(window.slots()).hasSize(3);
        assertThat(window.slots().get(0).isEmpty()).isTrue();
        assertThat(window.slots().get(1).is("book")).isTrue();
        assertThat(window.slots().get(1).amount()).isEqualTo(2);
        assertThat(window.slots().get(2).isEmpty()).isTrue();
    }

    @Test
    void anOpenWindowWithoutSlotsIsAnError() {
        assertThatThrownBy(() -> read("\"openWindow\":{\"type\":\"chest\",\"title\":\"x\"}}"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("no slots");
    }

    @Test
    void aWindowWithoutTypeOrTitleIsAnError() {
        assertThatThrownBy(() -> read("\"openWindow\":{\"slots\":[]}}"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("no type");
        assertThatThrownBy(() -> read("\"openWindow\":{\"type\":\" \",\"title\":\"x\",\"slots\":[]}}"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("no type");
        assertThatThrownBy(() -> read("\"openWindow\":{\"type\":\"chest\",\"slots\":[]}}"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("no title");
    }

    @Test
    void aNullOptionalIsRejected() {
        assertThatThrownBy(() -> new PlayerState("A", 0, 0, "survival", 20, java.util.Map.of(),
                io.github.h7kz.proofstone.api.ItemView.EMPTY, null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void theV0ConstructorHasNoWindow() {
        assertThat(new PlayerState("A", 0, 0, "survival", 20, java.util.Map.of(),
                io.github.h7kz.proofstone.api.ItemView.EMPTY).openWindow()).isEmpty();
    }
}
