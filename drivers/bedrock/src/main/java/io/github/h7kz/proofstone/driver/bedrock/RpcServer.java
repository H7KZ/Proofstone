package io.github.h7kz.proofstone.driver.bedrock;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.github.h7kz.proofstone.core.driver.bedrock.rpc.BedrockRpc;
import io.github.h7kz.proofstone.core.driver.bedrock.rpc.BedrockRpc.Ack;
import io.github.h7kz.proofstone.core.driver.bedrock.rpc.BedrockRpc.AwaitRequest;
import io.github.h7kz.proofstone.core.driver.bedrock.rpc.BedrockRpc.ChatRequest;
import io.github.h7kz.proofstone.core.driver.bedrock.rpc.BedrockRpc.CommandRequest;
import io.github.h7kz.proofstone.core.driver.bedrock.rpc.BedrockRpc.ConnectRequest;
import io.github.h7kz.proofstone.core.driver.bedrock.rpc.BedrockRpc.Connected;
import io.github.h7kz.proofstone.core.driver.bedrock.rpc.BedrockRpc.Envelope;
import io.github.h7kz.proofstone.core.driver.bedrock.rpc.BedrockRpc.Health;
import io.github.h7kz.proofstone.core.driver.bedrock.rpc.BedrockRpc.Inventory;
import io.github.h7kz.proofstone.core.driver.bedrock.rpc.BedrockRpc.Message;
import io.github.h7kz.proofstone.core.driver.bedrock.rpc.BedrockRpc.Messages;
import io.github.h7kz.proofstone.core.driver.bedrock.rpc.BedrockRpc.Position;
import io.github.h7kz.proofstone.core.driver.bedrock.rpc.BedrockRpc.RpcError;
import io.github.h7kz.proofstone.core.driver.bedrock.rpc.BedrockRpc.Transcript;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * The driver host's HTTP side: driver RPC v1 (client-driver spec section 6). A bearer token, the agent's envelope,
 * {@code X-Proofstone-Request-Id} echoed, and timeouts that answer {@code TIMEOUT}, never a partial result. State is
 * the server's: the host applies no prediction. It listens on loopback only; the harness calls it with curl inside
 * the container, so nothing is published.
 */
final class RpcServer implements AutoCloseable {

    /** One live Bedrock connection as the RPC sees it. */
    interface Connection {
        String name();

        String xuid();

        SessionState state();

        void chat(String message);

        void command(String command);

        void close();
    }

    /** Opens a connection to the spawn status, or throws {@link HostSession.HostError}. */
    interface Joiner {
        Connection join(String id, ConnectRequest request);
    }

    private static final String SESSIONS_PREFIX = BedrockRpc.SESSIONS + "/";
    private static final Duration MAX_AWAIT = Duration.ofMinutes(10);

    private final HttpServer http;
    private final byte[] token;
    private final Joiner joiner;
    private final Map<String, Connection> sessions = new ConcurrentHashMap<>();
    private final Set<String> xuids = new HashSet<>();

    RpcServer(int port, String token, Joiner joiner) throws IOException {
        this.token = token.getBytes(StandardCharsets.UTF_8);
        this.joiner = joiner;
        this.http = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 0);
        // Joins and awaits block for seconds; one thread each, daemon so a stuck call never keeps the host alive.
        this.http.setExecutor(Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "bedrock-rpc");
            thread.setDaemon(true);
            return thread;
        }));
        this.http.createContext(BedrockRpc.BASE, this::serve);
    }

    void start() {
        http.start();
    }

    int port() {
        return http.getAddress().getPort();
    }

    @Override
    public void close() {
        http.stop(0);
        sessions.values().forEach(Connection::close);
        sessions.clear();
    }

    private void serve(HttpExchange exchange) throws IOException {
        String requestId = exchange.getRequestHeaders().getFirst(BedrockRpc.REQUEST_ID_HEADER);
        exchange.getResponseHeaders().add(BedrockRpc.REQUEST_ID_HEADER, requestId == null ? UUID.randomUUID().toString() : requestId);
        int status = 200;
        Envelope<?> envelope;
        try {
            if (!authorised(exchange.getRequestHeaders().getFirst("Authorization"))) {
                throw new Failure(401, new RpcError(BedrockRpc.UNAUTHORIZED, "missing or wrong bearer token", null, null));
            }
            envelope = new Envelope<>(true, route(exchange.getRequestMethod(), exchange.getRequestURI().getPath(),
                    exchange.getRequestURI().getRawQuery(), exchange.getRequestBody().readAllBytes()), null);
        } catch (Failure failure) {
            status = failure.status;
            envelope = new Envelope<>(false, null, failure.error);
        } catch (RuntimeException e) {
            status = 500;
            envelope = new Envelope<>(false, null, new RpcError(BedrockRpc.INTERNAL, e.toString(), null, null));
        }
        byte[] body = BedrockRpc.JSON.writeValueAsBytes(envelope);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, body.length);
        try (var out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    private boolean authorised(String header) {
        String prefix = "Bearer ";
        return header != null && header.startsWith(prefix)
                && MessageDigest.isEqual(header.substring(prefix.length()).getBytes(StandardCharsets.UTF_8), token);
    }

    private Object route(String method, String path, String query, byte[] body) {
        if (path.equals(BedrockRpc.HEALTH) && method.equals("GET")) {
            return new Health(BedrockRpc.VERSION, BedrockRpc.PROTOCOL, BedrockRpc.GAME_VERSION);
        }
        if (path.equals(BedrockRpc.SESSIONS) && method.equals("POST")) {
            return connect(read(body, ConnectRequest.class));
        }
        if (!path.startsWith(SESSIONS_PREFIX)) {
            throw notFound(method + " " + path);
        }
        String[] parts = path.substring(SESSIONS_PREFIX.length()).split("/", -1);
        Connection connection = sessions.get(parts[0]);
        if (connection == null) {
            throw notFound("session " + parts[0]);
        }
        String action = parts.length == 1 ? "" : parts[1];
        if (parts.length > 2) {
            throw notFound(method + " " + path);
        }
        switch (method + " " + action) {
            case "DELETE " -> {
                disconnect(parts[0], connection);
                return new Ack(true);
            }
            case "POST chat" -> {
                connection.chat(requireText(read(body, ChatRequest.class).message(), "message"));
                return new Ack(true);
            }
            case "POST command" -> {
                connection.command(requireText(read(body, CommandRequest.class).command(), "command"));
                return new Ack(true);
            }
            case "POST await-message" -> {
                return await(connection, read(body, AwaitRequest.class));
            }
            case "GET messages" -> {
                return new Messages(connection.state().messagesSince(since(query)));
            }
            case "GET inventory" -> {
                return new Inventory(alive(connection).inventory());
            }
            case "GET position" -> {
                Position position = alive(connection).position();
                if (position == null) {
                    throw new Failure(409, new RpcError(BedrockRpc.PROTOCOL_ERROR, "the server has not synced a position", null, null));
                }
                return position;
            }
            case "GET transcript" -> {
                return new Transcript(connection.state().transcript());
            }
            default -> throw notFound(method + " " + path);
        }
    }

    private Connected connect(ConnectRequest request) {
        if (request.host() == null || request.host().isEmpty() || request.port() < 1 || request.port() > 65535
                || request.joinTimeoutMillis() <= 0) {
            throw badRequest("connect needs host, port 1 to 65535 and a positive joinTimeoutMillis");
        }
        try {
            BedrockRpc.requireName(request.name());
            BedrockRpc.requireXuid(request.xuid());
            BedrockRpc.requireTransport(request.transport());
        } catch (IllegalArgumentException e) {
            throw badRequest(e.getMessage());
        }
        synchronized (xuids) {
            if (!xuids.add(request.xuid())) {
                throw badRequest("XUID " + request.xuid() + " is already used by a live session: it must be unique per join");
            }
        }
        String id = UUID.randomUUID().toString();
        try {
            Connection connection = joiner.join(id, request);
            sessions.put(id, connection);
            return new Connected(id, "." + connection.name());
        } catch (HostSession.HostError e) {
            release(request.xuid());
            throw new Failure(e.toRpc().code().equals(BedrockRpc.JOIN_TIMEOUT) ? 408 : 409, e.toRpc());
        } catch (RuntimeException e) {
            release(request.xuid());
            throw e;
        }
    }

    private void disconnect(String id, Connection connection) {
        sessions.remove(id);
        connection.close();
        release(connection.xuid());
    }

    private void release(String xuid) {
        synchronized (xuids) {
            xuids.remove(xuid);
        }
    }

    private Object await(Connection connection, AwaitRequest request) {
        if (request.pattern() == null || request.timeoutMillis() <= 0
                || Duration.ofMillis(request.timeoutMillis()).compareTo(MAX_AWAIT) > 0) {
            throw badRequest("await-message needs a pattern and a timeoutMillis from 1 to " + MAX_AWAIT.toMillis());
        }
        Pattern pattern;
        try {
            pattern = Pattern.compile(request.pattern());
        } catch (PatternSyntaxException e) {
            throw badRequest("pattern is not a regular expression: " + e.getMessage());
        }
        Message message = connection.state().awaitMessage(pattern, Duration.ofMillis(request.timeoutMillis()));
        if (message != null) {
            return message;
        }
        SessionState.Failure failure = connection.state().failure();
        if (failure != null) {
            throw new Failure(409, SessionState.error(failure));
        }
        throw new Failure(408, new RpcError(BedrockRpc.TIMEOUT, "no message matching " + pattern + " within "
                + request.timeoutMillis() + " ms; unconsumed: " + connection.state().unconsumedText(), null, null));
    }

    /** A read of a session that ended is its failure, never a stale view. */
    private static SessionState alive(Connection connection) {
        SessionState.Failure failure = connection.state().failure();
        if (failure != null) {
            throw new Failure(409, SessionState.error(failure));
        }
        return connection.state();
    }

    private static long since(String query) {
        if (query == null || query.isEmpty()) {
            return 0;
        }
        if (!query.startsWith("since=")) {
            throw badRequest("unknown query " + query);
        }
        try {
            return Long.parseLong(query.substring("since=".length()));
        } catch (NumberFormatException e) {
            throw badRequest("since must be a number");
        }
    }

    private static String requireText(String text, String field) {
        if (text == null || text.isEmpty()) {
            throw badRequest(field + " must not be empty");
        }
        return text;
    }

    private static <T> T read(byte[] body, Class<T> type) {
        try {
            T value = BedrockRpc.JSON.readValue(body, type);
            if (value == null) {
                throw badRequest("the request has no body");
            }
            return value;
        } catch (JsonProcessingException e) {
            // An unknown field is an error, never ignored (AGENTS.md section 3).
            throw badRequest("the request is not RPC v1 " + type.getSimpleName() + ": " + e.getOriginalMessage());
        } catch (IOException e) {
            throw badRequest("the request body cannot be read: " + e.getMessage());
        }
    }

    private static Failure notFound(String what) {
        return new Failure(404, new RpcError(BedrockRpc.NOT_FOUND, "no such resource: " + what, null, null));
    }

    private static Failure badRequest(String why) {
        return new Failure(400, new RpcError(BedrockRpc.BAD_REQUEST, why, null, null));
    }

    /** A request that ends in an error reply with this HTTP status. */
    private static final class Failure extends RuntimeException {
        private static final long serialVersionUID = 1L;
        private final int status;
        private final transient RpcError error;

        Failure(int status, RpcError error) {
            super(error.message());
            this.status = status;
            this.error = error;
        }
    }
}
