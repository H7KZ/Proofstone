package io.github.h7kz.proofstone.agent.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.sun.net.httpserver.HttpExchange;
import io.github.h7kz.proofstone.agent.game.Fields;
import io.github.h7kz.proofstone.agent.game.Game;
import io.github.h7kz.proofstone.agent.protocol.AgentException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Routes for the observation endpoints (spec §2): world blocks, event probes, the log and ticks. Split from
 * {@link AgentServer} so the two M2 groups stay apart. Every route only reads or writes through {@link Game}.
 */
final class ObservationRoutes {

    private static final String WORLD_BLOCK = "/v1/world/block";
    private static final String EVENTS_PREFIX = "/v1/events/";
    private static final String SUBSCRIBE = "/v1/events/subscribe";
    private static final String LOG = "/v1/log";
    private static final String TICKS = "/v1/ticks";
    private static final String TICKS_AWAIT = "/v1/ticks/await";

    private final Game game;

    ObservationRoutes(Game game) {
        this.game = game;
    }

    /** True when the path belongs to one of the observation endpoints. */
    static boolean handles(String path) {
        return WORLD_BLOCK.equals(path) || path.startsWith(EVENTS_PREFIX) || LOG.equals(path)
                || TICKS.equals(path) || TICKS_AWAIT.equals(path);
    }

    Object dispatch(HttpExchange exchange, String method, String path) throws IOException {
        if (WORLD_BLOCK.equals(path)) {
            return worldBlock(exchange, method);
        }
        if (SUBSCRIBE.equals(path)) {
            AgentServer.requireMethod(method, "POST");
            JsonNode body = AgentServer.jsonBody(exchange);
            String probeId = game.subscribe(eventClass(body), AgentServer.timeoutMs(body));
            return Collections.singletonMap("probeId", probeId);
        }
        if (path.startsWith(EVENTS_PREFIX)) {
            return probe(exchange, method, path.substring(EVENTS_PREFIX.length()));
        }
        if (LOG.equals(path)) {
            AgentServer.requireMethod(method, "GET");
            Map<String, String> query = query(exchange);
            return game.log(sinceCursor(query), query.getOrDefault("minLevel", "TRACE"),
                    AgentServer.DEFAULT_TIMEOUT_MS);
        }
        if (TICKS.equals(path)) {
            AgentServer.requireMethod(method, "GET");
            return game.ticks(AgentServer.DEFAULT_TIMEOUT_MS);
        }
        AgentServer.requireMethod(method, "POST");
        JsonNode body = AgentServer.jsonBody(exchange);
        return game.awaitTicks(awaitedTicks(body), AgentServer.timeoutMs(body));
    }

    /** {@code GET} reads the block at the query position; {@code PUT} writes the body and replies with the block. */
    private Object worldBlock(HttpExchange exchange, String method) throws IOException {
        Map<String, String> query = query(exchange);
        String world = required(query, "world");
        int x = integer(query, "x");
        int y = integer(query, "y");
        int z = integer(query, "z");
        if ("GET".equals(method)) {
            return game.block(world, x, y, z, AgentServer.DEFAULT_TIMEOUT_MS);
        }
        AgentServer.requireMethod(method, "PUT");
        JsonNode body = AgentServer.jsonBody(exchange);
        Map<String, Object> write = AgentServer.fields(body);
        Fields.onlyFields(write, Arrays.asList("type", "state"), "block");
        return game.setBlock(world, x, y, z, write, AgentServer.timeoutMs(body));
    }

    /** {@code GET /v1/events/{id}?since=} reads the probe; {@code DELETE} unsubscribes it. */
    private Object probe(HttpExchange exchange, String method, String probeId) throws IOException {
        if ("DELETE".equals(method)) {
            game.unsubscribe(probeId, AgentServer.DEFAULT_TIMEOUT_MS);
            return Collections.emptyMap();
        }
        AgentServer.requireMethod(method, "GET");
        return game.events(probeId, sinceCursor(query(exchange)), AgentServer.DEFAULT_TIMEOUT_MS);
    }

    private static String eventClass(JsonNode body) {
        JsonNode node = body.get("class");
        if (node == null || !node.isTextual() || node.asText().isEmpty()) {
            throw Fields.invalid("\"class\" must be a class name");
        }
        return node.asText();
    }

    private static long awaitedTicks(JsonNode body) {
        JsonNode node = body.get("n");
        if (node == null || !node.isIntegralNumber() || node.asLong() < 1) {
            throw Fields.invalid("\"n\" must be an integer of at least 1");
        }
        return node.asLong();
    }

    private static long sinceCursor(Map<String, String> query) {
        String since = query.get("since");
        if (since == null) {
            return 0;
        }
        try {
            long value = Long.parseLong(since);
            if (value < 0) {
                throw Fields.invalid("\"since\" must not be negative");
            }
            return value;
        } catch (NumberFormatException e) {
            throw Fields.invalid("\"since\" must be an integer");
        }
    }

    private static String required(Map<String, String> query, String name) {
        String value = query.get(name);
        if (value == null || value.isEmpty()) {
            throw Fields.invalid("query parameter \"" + name + "\" is required");
        }
        return value;
    }

    private static int integer(Map<String, String> query, String name) {
        try {
            return Integer.parseInt(required(query, name));
        } catch (NumberFormatException e) {
            throw Fields.invalid("query parameter \"" + name + "\" must be an integer");
        }
    }

    /** The decoded query string. A repeated key keeps its last value. */
    private static Map<String, String> query(HttpExchange exchange) {
        Map<String, String> out = new HashMap<>();
        String raw = exchange.getRequestURI().getRawQuery();
        if (raw == null || raw.isEmpty()) {
            return out;
        }
        for (String pair : raw.split("&")) {
            int eq = pair.indexOf('=');
            String key = decode(eq < 0 ? pair : pair.substring(0, eq));
            String value = eq < 0 ? "" : decode(pair.substring(eq + 1));
            out.put(key, value);
        }
        return out;
    }

    private static String decode(String text) {
        try {
            return URLDecoder.decode(text, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new AgentException(500, "INTERNAL", "UTF-8 is missing: " + e, null);
        }
    }
}
