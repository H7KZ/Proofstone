package dev.proofstone.agent.server;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.Filter;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.proofstone.agent.protocol.Envelope;
import dev.proofstone.agent.protocol.Health;
import dev.proofstone.agent.protocol.ProtocolError;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;

/**
 * HTTP server for the agent (spec §1–§2). Only {@code /v1/health} exists in M1. Binds all interfaces
 * because it runs inside the container network and is never published by default (spec §5).
 */
public final class AgentServer {

    static final ObjectMapper MAPPER = new ObjectMapper()
            .setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL);

    private final HttpServer server;

    public AgentServer(AgentConfig config, Health health) throws IOException {
        this.server = HttpServer.create(new InetSocketAddress("0.0.0.0", config.port()), 0);
        server.createContext("/v1/health", exchange -> {
            if (!"GET".equals(exchange.getRequestMethod())) {
                write(exchange, 405, error("METHOD_NOT_ALLOWED", "use GET"));
                return;
            }
            write(exchange, 200, MAPPER.writeValueAsBytes(new Envelope<>(true, health, null)));
        }).getFilters().add(new BearerFilter(config.token()));
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

    private static byte[] error(String code, String message) throws IOException {
        return MAPPER.writeValueAsBytes(new Envelope<Health>(false, null, new ProtocolError(code, message, null)));
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
            write(exchange, 401, error("UNAUTHORISED", "missing or wrong bearer token"));
        }

        @Override
        public String description() {
            return "bearer token";
        }
    }
}
