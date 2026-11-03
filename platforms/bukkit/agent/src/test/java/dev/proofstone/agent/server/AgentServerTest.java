package dev.proofstone.agent.server;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.proofstone.agent.protocol.Health;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Real HTTP round trip on an ephemeral port. Health is checked against inline examples until T-M1-02. */
class AgentServerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String TOKEN = "s3cret";
    private static final String HEALTH_JSON = "{\"ok\":true,\"data\":{\"agentVersion\":\"0.1.0\","
            + "\"protocol\":\"1.0\",\"platform\":\"paper\",\"mcVersion\":\"1.8.8\",\"capabilities\":[]}}";

    private AgentServer server;

    @BeforeEach
    void start() throws IOException {
        Health health = new Health("0.1.0", "1.0", "paper", "1.8.8", Collections.<String>emptyList());
        server = new AgentServer(new AgentConfig(TOKEN, 0), health);
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop();
    }

    @Test
    void healthWithTheTokenMatchesTheExample() throws IOException {
        Response response = get("/v1/health", "Bearer " + TOKEN);

        assertThat(response.status).isEqualTo(200);
        assertThat(MAPPER.readTree(response.body)).isEqualTo(MAPPER.readTree(HEALTH_JSON));
    }

    @Test
    void healthWithoutOrWithAWrongTokenIs401() throws IOException {
        assertThat(get("/v1/health", null).status).isEqualTo(401);
        assertThat(get("/v1/health", "Bearer nope").status).isEqualTo(401);
    }

    private Response get(String path, String authorization) throws IOException {
        URL url = new URL("http://127.0.0.1:" + server.port() + path);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        if (authorization != null) {
            connection.setRequestProperty("Authorization", authorization);
        }
        int status = connection.getResponseCode();
        InputStream stream = status < 400 ? connection.getInputStream() : connection.getErrorStream();
        return new Response(status, read(stream));
    }

    private static String read(InputStream in) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[1024];
        int n;
        while ((n = in.read(chunk)) != -1) {
            buffer.write(chunk, 0, n);
        }
        return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
    }

    private static final class Response {
        final int status;
        final String body;

        Response(int status, String body) {
            this.status = status;
            this.body = body;
        }
    }
}
