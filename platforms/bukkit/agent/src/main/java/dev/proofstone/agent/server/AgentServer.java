package dev.proofstone.agent.server;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.Filter;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.proofstone.agent.game.Game;
import dev.proofstone.agent.protocol.AgentException;
import dev.proofstone.agent.protocol.Envelope;
import dev.proofstone.agent.protocol.Health;
import dev.proofstone.agent.protocol.ProtocolError;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.Collections;

/**
 * HTTP server for the agent (spec §1–§2). It handles routing, auth, bodies and envelopes. Game state comes
 * from a {@link Game}. Binds all interfaces because it runs inside the container network and is never
 * published by default (spec §5).
 */
public final class AgentServer {

    // Bean properties omit nulls (an absent error, for one). Map entries keep them: PlayerState carries
    // "cursor": null and "openWindow": null as explicit values (golden/players).
    static final ObjectMapper MAPPER = new ObjectMapper()
            .setDefaultPropertyInclusion(JsonInclude.Value.construct(
                    JsonInclude.Include.NON_NULL, JsonInclude.Include.ALWAYS));

    /** Spec §1: a request body may set {@code timeoutMs}; this is the default. */
    static final long DEFAULT_TIMEOUT_MS = 10_000;

    private static final String PLAYERS = "/v1/players";
    private static final String PLAYERS_PREFIX = PLAYERS + "/";

    private final HttpServer server;
    private final Health health;
    private final Game game;

    public AgentServer(AgentConfig config, Health health, Game game) throws IOException {
        this.server = HttpServer.create(new InetSocketAddress("0.0.0.0", config.port()), 0);
        this.health = health;
        this.game = game;
        server.createContext("/v1/", this::handle).getFilters().add(new BearerFilter(config.token()));
    }

    /** Server port actually bound; differs from the config when the config asked for port 0. */
    public int port() {
        return server.getAddress().getPort();
    }

    public void start() {
        server.start();
    }

    public void stop() {
        server.stop(0);
    }

    private void handle(HttpExchange exchange) throws IOException {
        try {
            Object data = dispatch(exchange);
            write(exchange, 200, MAPPER.writeValueAsBytes(new Envelope<Object>(true, data, null)));
        } catch (AgentException e) {
            write(exchange, e.status(), error(e));
        } catch (RuntimeException e) {
            // A bug must reach the harness as an error envelope, not as an empty reply.
            write(exchange, 500, error(new AgentException(500, "INTERNAL", String.valueOf(e), null)));
        }
    }

    private Object dispatch(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();
        if ("/v1/health".equals(path)) {
            requireMethod(method, "GET");
            return health;
        }
        if ("/v1/command".equals(path)) {
            requireMethod(method, "POST");
            JsonNode body = jsonBody(exchange);
            return Collections.singletonMap("raw", game.command(text(body, "command"), timeoutMs(body)));
        }
        if (PLAYERS.equals(path)) {
            requireMethod(method, "GET");
            return Collections.singletonMap("players", game.players(DEFAULT_TIMEOUT_MS));
        }
        if (path.startsWith(PLAYERS_PREFIX) && path.indexOf('/', PLAYERS_PREFIX.length()) < 0) {
            requireMethod(method, "GET");
            return game.player(path.substring(PLAYERS_PREFIX.length()), DEFAULT_TIMEOUT_MS);
        }
        throw new AgentException(404, "NOT_FOUND", "no endpoint at " + path, null);
    }

    private static void requireMethod(String actual, String expected) {
        if (!expected.equals(actual)) {
            throw new AgentException(405, "METHOD_NOT_ALLOWED", "use " + expected, null);
        }
    }

    private static JsonNode jsonBody(HttpExchange exchange) throws IOException {
        JsonNode body;
        try {
            body = MAPPER.readTree(exchange.getRequestBody());
        } catch (JsonProcessingException e) {
            throw invalid("body is not valid JSON");
        }
        if (body == null || !body.isObject()) {
            throw invalid("body must be a JSON object");
        }
        return body;
    }

    private static String text(JsonNode body, String field) {
        JsonNode node = body.get(field);
        if (node == null || !node.isTextual()) {
            throw invalid("\"" + field + "\" must be a string");
        }
        return node.asText();
    }

    /** Explicit {@code timeoutMs} must be positive. A malformed value is an error, never the default. */
    private static long timeoutMs(JsonNode body) {
        JsonNode node = body.get("timeoutMs");
        if (node == null) {
            return DEFAULT_TIMEOUT_MS;
        }
        if (!node.isIntegralNumber() || node.asLong() <= 0) {
            throw invalid("\"timeoutMs\" must be a positive integer");
        }
        return node.asLong();
    }

    private static AgentException invalid(String message) {
        return new AgentException(400, "INVALID_REQUEST", message, null);
    }

    private static byte[] error(AgentException e) throws IOException {
        return MAPPER.writeValueAsBytes(new Envelope<Object>(false, null,
                new ProtocolError(e.code(), e.getMessage(), e.detail())));
    }

    private static void write(HttpExchange exchange, int status, byte[] body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    /** Rejects requests without the right bearer token with 401 before any handler runs. */
    private static final class BearerFilter extends Filter {

        private final String token;

        BearerFilter(String token) {
            this.token = token;
        }

        @Override
        public void doFilter(HttpExchange exchange, Chain chain) throws IOException {
            String header = exchange.getRequestHeaders().getFirst("Authorization");
            if (BearerAuth.authorised(header, token)) {
                chain.doFilter(exchange);
                return;
            }
            byte[] body = error(new AgentException(401, "UNAUTHORIZED", "missing or wrong bearer token", null));
            write(exchange, 401, body);
        }

        @Override
        public String description() {
            return "bearer token";
        }
    }
}
