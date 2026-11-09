package io.github.h7kz.proofstone.agent.bungee;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/** Calls the agent over HTTP, the way the harness does. */
final class AgentClient {

    static final String TOKEN = "s3cret";
    static final ObjectMapper JSON = new ObjectMapper();

    /** Status and raw body of one reply. */
    static final class Reply {
        final int status;
        final String body;

        Reply(int status, String body) {
            this.status = status;
            this.body = body;
        }

        JsonNode json() throws IOException {
            return JSON.readTree(body);
        }

        String errorCode() throws IOException {
            return json().get("error").get("code").asText();
        }
    }

    private final HttpClient http = HttpClient.newHttpClient();
    private final int port;

    AgentClient(int port) {
        this.port = port;
    }

    Reply send(String method, String path, String authorization, String body) throws IOException {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(20));
        if (authorization != null) {
            request.header("Authorization", authorization);
        }
        request.method(method, body == null
                ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        try {
            HttpResponse<byte[]> reply = http.send(request.build(), HttpResponse.BodyHandlers.ofByteArray());
            return new Reply(reply.statusCode(), new String(reply.body(), StandardCharsets.UTF_8));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted", e);
        }
    }

    Reply get(String path) throws IOException {
        return send("GET", path, "Bearer " + TOKEN, null);
    }

    Reply post(String path, String body) throws IOException {
        return send("POST", path, "Bearer " + TOKEN, body);
    }
}
