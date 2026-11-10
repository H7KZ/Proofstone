package io.github.h7kz.proofstone.core.host;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.h7kz.proofstone.protocol.Envelope;
import io.github.h7kz.proofstone.protocol.Health;
import java.io.IOException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.containers.GenericContainer;

/**
 * Calls an agent by running {@code curl} inside its container, so no agent port is published (T-M1-07). Shared by
 * {@link PaperHost} and {@link ProxyHost}. Every call is recorded for the repro bundle; the token never is, because
 * headers are not recorded.
 */
final class AgentClient {

    static final int AGENT_PORT = 25580;
    private static final String AGENT_URL = "http://127.0.0.1:" + AGENT_PORT;
    private static final int TRANSCRIPT_TEXT_LIMIT = 500;
    // Absent optional fields are omitted: the agent rejects an explicit "timeoutMs": null (found in T-M1-08).
    static final ObjectMapper JSON = new ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL);

    private final GenericContainer<?> container;
    private final String token;
    /** One JSON line per agent call, for the repro bundle. */
    private final List<String> transcript = Collections.synchronizedList(new ArrayList<>());

    AgentClient(GenericContainer<?> container, String token) {
        this.container = container;
        this.token = token;
    }

    /** A new agent token: random per server, never logged. */
    static String randomToken() {
        byte[] bytes = new byte[24];
        new SecureRandom().nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    /** The agent's health, or an exception while it is not up yet; a protocol other than 1.x is an error. */
    Health health() {
        Envelope<Health> envelope = parse(call("GET", "/v1/health", null), new TypeReference<>() { });
        if (!envelope.ok() || envelope.data() == null) {
            throw new IllegalStateException("agent health not ok: " + envelope.error());
        }
        if (!envelope.data().protocol().startsWith("1.")) {
            throw new PaperHostException("agent protocol " + envelope.data().protocol() + " is not 1.x");
        }
        return envelope.data();
    }

    static <T> T parse(String json, TypeReference<T> type) {
        try {
            return JSON.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("unparsable agent reply: " + json, e);
        }
    }

    String call(String method, String path, String body) {
        String requestId = UUID.randomUUID().toString();
        List<String> args = new ArrayList<>(List.of("curl", "-sS", "-m", "5", "-X", method,
                "-H", "Authorization: Bearer " + token, "-H", "Content-Type: application/json",
                "-H", "X-Proofstone-Request-Id: " + requestId));
        if (body != null) {
            args.addAll(List.of("-d", body));
        }
        args.add(AGENT_URL + path);
        String call = method + " " + path;
        try {
            String reply = exec(args.toArray(String[]::new));
            record(requestId, call, body, reply);
            return reply;
        } catch (RuntimeException e) {
            record(requestId, call, body, "error: " + e.getMessage());
            throw e;
        }
    }

    private void record(String requestId, String call, String body, String reply) {
        ObjectNode line = JSON.createObjectNode()
                .put("at", Instant.now().toString())
                .put("requestId", requestId)
                .put("call", call);
        if (body != null) {
            line.put("request", truncate(body));
        }
        line.put("reply", truncate(reply));
        transcript.add(line.toString());
    }

    /** Keeps a recorded reply short; the full output is in the server log. */
    private static String truncate(String text) {
        return text.length() <= TRANSCRIPT_TEXT_LIMIT ? text : text.substring(0, TRANSCRIPT_TEXT_LIMIT) + "...";
    }

    List<String> transcript() {
        synchronized (transcript) {
            return new ArrayList<>(transcript);
        }
    }

    /** Runs a command in the container; a non-zero exit is an error with its stderr. */
    String exec(String... args) {
        try {
            ExecResult result = container.execInContainer(args);
            if (result.getExitCode() != 0) {
                throw new IllegalStateException(args[0] + " exited " + result.getExitCode() + ": "
                        + result.getStderr());
            }
            return result.getStdout() + result.getStderr();
        } catch (IOException e) {
            throw new IllegalStateException("exec " + args[0] + " failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("exec " + args[0] + " interrupted", e);
        }
    }
}
