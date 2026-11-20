package io.github.h7kz.proofstone.core.junit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.h7kz.proofstone.api.BlockPos;
import io.github.h7kz.proofstone.core.host.AgentEndpoint;
import io.github.h7kz.proofstone.core.host.LogBuffer;
import io.github.h7kz.proofstone.protocol.Health;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** {@code Agent.blockType} over a fake agent: the request it sends and what it does with odd replies (T-M11-04). */
class HostAgentBlockTypeTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static final class FakeAgent implements AgentEndpoint {

        final List<String> requests = new ArrayList<>();
        String reply;

        @Override
        public JsonNode request(String method, String path, Object body) {
            requests.add(method + " " + path);
            try {
                return JSON.readTree(reply);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
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
            return "token";
        }

        @Override
        public List<String> agentTranscript() {
            return List.of();
        }

        @Override
        public LogBuffer logs() {
            throw new UnsupportedOperationException();
        }
    }

    private static HostAgent agent(FakeAgent fake) {
        return new HostAgent(fake, Set.of(), () -> 0L);
    }

    @Test
    void readsTheTypeFromTheWorldBlockEndpointWithTheEncodedWorldAndNegativeCoordinates() {
        FakeAgent fake = new FakeAgent();
        fake.reply = "{\"world\":\"my world\",\"x\":-1,\"y\":64,\"z\":2,\"type\":\"minecraft:anvil\","
                + "\"state\":{\"facing\":\"north\"}}";

        assertThat(agent(fake).blockType("my world", new BlockPos(-1, 64, 2))).isEqualTo("minecraft:anvil");
        assertThat(fake.requests).containsExactly("GET /v1/world/block?world=my+world&x=-1&y=64&z=2");
    }

    @Test
    void aReplyWithoutATypeIsAnErrorNotAnEmptyAnswer() {
        FakeAgent fake = new FakeAgent();
        fake.reply = "{\"world\":\"world\",\"x\":0,\"y\":64,\"z\":0,\"state\":{}}";

        assertThatThrownBy(() -> agent(fake).blockType("world", new BlockPos(0, 64, 0)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("no block type");
    }
}
