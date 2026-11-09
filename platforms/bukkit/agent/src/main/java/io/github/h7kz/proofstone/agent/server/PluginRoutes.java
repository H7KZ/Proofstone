package io.github.h7kz.proofstone.agent.server;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.sun.net.httpserver.HttpExchange;
import io.github.h7kz.proofstone.agent.game.Fields;
import io.github.h7kz.proofstone.agent.game.Game;
import io.github.h7kz.proofstone.agent.protocol.AgentException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Routes under {@code /v1/plugins/{id}/} (spec §2): config read and PATCH, config restore, and reload. The shape of
 * each request is checked here; the plugin work is in {@link Game}. Unknown body fields are {@code INVALID_REQUEST}.
 */
final class PluginRoutes {

    /** The reload hook field of a reload body (reload.json). */
    static final String RELOAD_COMMAND = "proofstone-reload-command";

    private PluginRoutes() {
    }

    /** {@code rest} is the path after {@code /v1/plugins/}, for example {@code {id}/config}. */
    static Object dispatch(Game game, HttpExchange exchange, String method, String rest) throws IOException {
        int slash = rest.indexOf('/');
        if (slash <= 0) {
            throw notFound(rest);
        }
        String id = rest.substring(0, slash);
        String action = rest.substring(slash + 1);
        switch (action) {
            case "config":
                if ("PATCH".equals(method)) {
                    return patch(game, exchange, id);
                }
                AgentServer.requireMethod(method, "GET");
                return read(game, exchange, id);
            case "config/restore":
                AgentServer.requireMethod(method, "POST");
                game.restorePluginConfig(id, AgentServer.DEFAULT_TIMEOUT_MS);
                return Collections.emptyMap();
            case "reload":
                AgentServer.requireMethod(method, "POST");
                return reload(game, exchange, id);
            default:
                throw notFound(rest);
        }
    }

    /** {@code GET /config?path=…} replies {@code {path, value}} (config.json#/$defs/read). */
    private static Object read(Game game, HttpExchange exchange, String id) {
        String path = queryParam(exchange, "path");
        if (path == null) {
            throw Fields.invalid("GET config needs a ?path= query parameter");
        }
        Map<String, Object> reply = new LinkedHashMap<>();
        reply.put("path", path);
        reply.put("value", game.pluginConfig(id, path, AgentServer.DEFAULT_TIMEOUT_MS));
        return reply;
    }

    /** {@code PATCH /config} takes {@code {path, value}} (config.json#/$defs/patch) and replies {}. */
    private static Object patch(Game game, HttpExchange exchange, String id) throws IOException {
        JsonNode body = AgentServer.jsonBody(exchange);
        Map<String, Object> fields = AgentServer.fields(body);
        Fields.onlyFields(fields, Arrays.asList("path", "value"), "config patch");
        String path = Fields.text(fields, "path");
        if (!fields.containsKey("value")) {
            throw Fields.invalid("\"value\" is required");
        }
        game.patchPluginConfig(id, path, fields.get("value"), AgentServer.timeoutMs(body));
        return Collections.emptyMap();
    }

    /**
     * {@code POST /reload}, with an optional body (reload.json). With no body the plugin is disabled and enabled;
     * with {@code proofstone-reload-command} that console command is the declared hook.
     */
    private static Object reload(Game game, HttpExchange exchange, String id) throws IOException {
        byte[] bytes = readAll(exchange);
        if (bytes.length == 0) {
            game.reloadPlugin(id, null, AgentServer.DEFAULT_TIMEOUT_MS);
            return Collections.emptyMap();
        }
        JsonNode body;
        try {
            body = AgentServer.MAPPER.readTree(bytes);
        } catch (JsonProcessingException e) {
            throw Fields.invalid("body is not valid JSON");
        }
        if (body == null || !body.isObject()) {
            throw Fields.invalid("body must be a JSON object");
        }
        Map<String, Object> fields = AgentServer.fields(body);
        Fields.onlyFields(fields, Collections.singletonList(RELOAD_COMMAND), "reload");
        String hook = Fields.optionalText(fields, RELOAD_COMMAND);
        if (hook != null && hook.trim().isEmpty()) {
            throw Fields.invalid("\"" + RELOAD_COMMAND + "\" must not be blank");
        }
        game.reloadPlugin(id, hook, AgentServer.timeoutMs(body));
        return Collections.emptyMap();
    }

    /** The value of {@code name} in the query string, or null. Config paths carry no escapes (see the pattern). */
    private static String queryParam(HttpExchange exchange, String name) {
        String query = exchange.getRequestURI().getRawQuery();
        if (query == null) {
            return null;
        }
        for (String pair : query.split("&")) {
            int equals = pair.indexOf('=');
            String key = equals < 0 ? pair : pair.substring(0, equals);
            if (name.equals(key)) {
                return equals < 0 ? "" : pair.substring(equals + 1);
            }
        }
        return null;
    }

    private static byte[] readAll(HttpExchange exchange) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (InputStream in = exchange.getRequestBody()) {
            byte[] buffer = new byte[1024];
            for (int n = in.read(buffer); n >= 0; n = in.read(buffer)) {
                out.write(buffer, 0, n);
            }
        }
        return out.toByteArray();
    }

    private static AgentException notFound(String rest) {
        return new AgentException(404, "NOT_FOUND", "no endpoint at /v1/plugins/" + rest, null);
    }
}
