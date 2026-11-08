package dev.proofstone.driver.mineflayer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.proofstone.api.BlockPos;
import dev.proofstone.core.await.Await;
import dev.proofstone.core.await.AwaitTimeoutException;
import dev.proofstone.core.driver.ChatMessage;
import dev.proofstone.core.driver.ClientDriverException;
import dev.proofstone.core.driver.ClientSession;
import dev.proofstone.core.driver.ConnectSpec;
import dev.proofstone.core.driver.Face;
import dev.proofstone.core.driver.WindowSession;
import dev.proofstone.core.driver.WindowView;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

/**
 * One Mineflayer bot in the sidecar, seen through JSON-RPC 2.0 over a WebSocket (client-driver spec section 4).
 * The sidecar pushes chat and disconnect as notifications; this class buffers them, so a reply that beats
 * {@link #awaitMessage} is not lost. Every value it returns is what the sidecar read from the server.
 *
 * <p>Windows, blocks and the player inventory come from the sidecar's syncs, which Mineflayer's own predictions
 * never touch (T-M4-05b).
 */
final class MineflayerRefSession implements ClientSession {

    private static final Duration RPC_WAIT = Duration.ofSeconds(20);

    private final ObjectMapper mapper = new ObjectMapper();
    private final String username;
    private final AtomicLong ids = new AtomicLong();
    private final Map<Long, CompletableFuture<JsonNode>> pending = new ConcurrentHashMap<>();
    private final List<ChatMessage> chat = Collections.synchronizedList(new ArrayList<>());
    private final List<String> transcript = Collections.synchronizedList(new ArrayList<>());
    /** Ids of windows the server opened that no awaitWindow has returned yet, in order. */
    private final List<Integer> opened = Collections.synchronizedList(new ArrayList<>());
    private volatile WebSocket socket;
    private volatile boolean disconnected;
    private volatile String disconnectReason;

    private MineflayerRefSession(String username) {
        this.username = username;
    }

    /** Opens the sidecar socket and joins the server through the sidecar; returns once the bot has spawned. */
    static MineflayerRefSession open(URI sidecar, ConnectSpec spec) {
        MineflayerRefSession session = new MineflayerRefSession(spec.username());
        if (MineflayerRefFault.enabled()) {
            // The repro bundle shows the fault next to the observations it changed (T-M4-06).
            session.transcript.add("fault injected: " + MineflayerRefDriver.FAULT_PROPERTY + "=true");
        }
        String where = spec.username() + " -> " + spec.host() + ":" + spec.port() + " via " + sidecar;
        try {
            session.socket = HttpClient.newHttpClient().newWebSocketBuilder()
                    .connectTimeout(spec.connectTimeout())
                    .buildAsync(sidecar, new Listener(session))
                    .get(spec.connectTimeout().toMillis(), TimeUnit.MILLISECONDS);
        } catch (ExecutionException | TimeoutException e) {
            throw new ClientDriverException("cannot reach the reference sidecar for " + where + ": " + e, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ClientDriverException("interrupted while reaching the sidecar for " + where, e);
        }
        ObjectNode params = session.mapper.createObjectNode()
                .put("host", spec.host())
                .put("port", spec.port())
                .put("username", spec.username());
        try {
            session.call("connect", params, spec.connectTimeout());
        } catch (ClientDriverException e) {
            session.close();
            throw new ClientDriverException("could not join " + where + ": " + e.getMessage(), e);
        }
        return session;
    }

    @Override
    public void chat(String message) {
        call("chat", params("message", message), RPC_WAIT);
    }

    @Override
    public void command(String commandWithoutSlash) {
        call("command", params("command", commandWithoutSlash), RPC_WAIT);
    }

    @Override
    public ChatMessage awaitMessage(Pattern pattern, Duration timeout) {
        Optional<ChatMessage> found;
        try {
            // A disconnect ends the wait with a value, so it is reported with its reason and never waits out the timeout.
            found = Await.until("chat message matching " + pattern + " for " + username, () -> take(pattern),
                    match -> match.isPresent() || disconnected, timeout);
        } catch (AwaitTimeoutException e) {
            throw new ClientDriverException("no message matching " + pattern + " within " + timeout
                    + "; unconsumed: " + chat, e);
        }
        if (found.isEmpty()) {
            throw new ClientDriverException("disconnected while waiting for " + pattern + ": " + disconnectReason
                    + "; unconsumed: " + chat);
        }
        return found.get();
    }

    @Override
    public void useBlock(BlockPos pos, Face face) {
        // The face is not sent: Mineflayer activates the block from its own look at it, which opens the containers
        // the scenarios use (chest, anvil, grindstone) whatever face the caller names.
        call("useBlock", mapper.createObjectNode().put("x", pos.x()).put("y", pos.y()).put("z", pos.z()),
                RPC_WAIT);
    }

    @Override
    public WindowSession awaitWindow(Duration timeout) {
        Optional<Integer> opened;
        try {
            opened = Await.until("a window opened for " + username, this::takeOpened, found -> found.isPresent()
                    || disconnected, timeout);
        } catch (AwaitTimeoutException e) {
            throw new ClientDriverException("no window opened within " + timeout + "; transcript tail: "
                    + transcript(), e);
        }
        if (opened.isEmpty()) {
            throw new ClientDriverException("disconnected while waiting for a window: " + disconnectReason);
        }
        return new MineflayerRefWindow(this, opened.get());
    }

    @Override
    public WindowView inventory() {
        return new MineflayerRefWindow(this, 0, MineflayerRefFault.enabled());
    }

    private Optional<Integer> takeOpened() {
        synchronized (opened) {
            return opened.isEmpty() ? Optional.empty() : Optional.of(opened.remove(0));
        }
    }

    @Override
    public int experienceLevel() {
        JsonNode level = state().get("experienceLevel");
        if (level == null || level.isNull()) {
            throw new ClientDriverException("the server has not synced an experience level yet");
        }
        return level.asInt();
    }

    @Override
    public double[] position() {
        JsonNode position = state().get("position");
        if (position == null || position.isNull()) {
            return null;
        }
        return new double[] {position.get(0).asDouble(), position.get(1).asDouble(), position.get(2).asDouble()};
    }

    @Override
    public List<String> transcript() {
        synchronized (transcript) {
            return List.copyOf(transcript);
        }
    }

    @Override
    public void close() {
        WebSocket open = socket;
        if (open == null) {
            return;
        }
        try {
            if (!disconnected) {
                call("close", mapper.createObjectNode(), Duration.ofSeconds(5));
            }
        } catch (ClientDriverException e) {
            transcript.add("close: " + e.getMessage());
        }
        socket = null;
        open.sendClose(WebSocket.NORMAL_CLOSURE, "proofstone closing");
    }

    private JsonNode state() {
        return call("state", mapper.createObjectNode(), RPC_WAIT);
    }

    private ObjectNode params(String key, String value) {
        return mapper.createObjectNode().put(key, value);
    }

    /** An empty params object, for the windows to add their fields to. */
    ObjectNode params() {
        return mapper.createObjectNode();
    }

    /** Sends one request and waits for its reply, which the listener completes. Errors carry the sidecar's message. */
    JsonNode call(String method, ObjectNode params, Duration timeout) {
        WebSocket open = socket;
        if (open == null || disconnected) {
            throw new ClientDriverException(method + " on a closed session: " + disconnectReason);
        }
        long id = ids.incrementAndGet();
        ObjectNode request = mapper.createObjectNode().put("jsonrpc", "2.0").put("id", id).put("method", method);
        request.set("params", params);
        CompletableFuture<JsonNode> reply = new CompletableFuture<>();
        pending.put(id, reply);
        transcript.add("-> " + method + " #" + id);
        open.sendText(request.toString(), true).join();
        try {
            Await.until("reply to " + method, reply::isDone, done -> done, timeout);
        } catch (AwaitTimeoutException e) {
            pending.remove(id);
            throw new ClientDriverException(method + " got no reply within " + timeout + "; transcript tail: "
                    + transcript(), e);
        }
        try {
            return reply.join();
        } catch (CompletionException e) {
            throw new ClientDriverException(method + " failed: " + e.getCause().getMessage(), e.getCause());
        }
    }

    private Optional<ChatMessage> take(Pattern pattern) {
        synchronized (chat) {
            for (Iterator<ChatMessage> it = chat.iterator(); it.hasNext();) {
                ChatMessage candidate = it.next();
                if (pattern.matcher(candidate.text()).find()) {
                    it.remove();
                    return Optional.of(candidate);
                }
            }
        }
        return Optional.empty();
    }

    /** Runs on the WebSocket's thread: completes a reply, or records a notification. */
    private void receive(String text) {
        JsonNode message;
        try {
            message = mapper.readTree(text);
        } catch (JsonProcessingException e) {
            endConnection("unparsable sidecar message: " + text);
            return;
        }
        if (message.hasNonNull("id")) {
            CompletableFuture<JsonNode> reply = pending.remove(message.get("id").asLong());
            if (reply == null) {
                transcript.add("<- unknown reply " + message.get("id"));
            } else if (message.hasNonNull("error")) {
                reply.completeExceptionally(new ClientDriverException(message.get("error").path("message").asText()));
            } else {
                reply.complete(message.path("result"));
            }
            return;
        }
        String method = message.path("method").asText();
        JsonNode params = message.path("params");
        switch (method) {
            case "chat" -> {
                chat.add(new ChatMessage(params.path("text").asText(), params.path("system").asBoolean()));
                transcript.add("<- chat");
            }
            case "disconnect" -> endConnection("server or bot disconnected: " + params.path("reason").asText());
            case "error" -> transcript.add("<- error " + params.path("message").asText());
            case "windowOpen" -> {
                opened.add(params.path("id").asInt());
                transcript.add("<- windowOpen " + params.path("type").asText());
            }
            case "windowClose" -> transcript.add("<- windowClose " + params.path("id").asInt());
            default -> endConnection("unknown sidecar notification: " + method);
        }
    }

    private void endConnection(String reason) {
        if (!disconnected) {
            disconnected = true;
            disconnectReason = reason;
            transcript.add("<- " + reason);
        }
        failAll(reason);
    }

    private void failAll(String reason) {
        for (Map.Entry<Long, CompletableFuture<JsonNode>> entry : pending.entrySet()) {
            if (pending.remove(entry.getKey(), entry.getValue())) {
                entry.getValue().completeExceptionally(new ClientDriverException(reason));
            }
        }
    }

    /** Feeds WebSocket frames to the session. Frames can be split, so text is joined until its last part. */
    private static final class Listener implements WebSocket.Listener {

        private final MineflayerRefSession session;
        private final StringBuilder partial = new StringBuilder();

        Listener(MineflayerRefSession session) {
            this.session = session;
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            partial.append(data);
            if (last) {
                String text = partial.toString();
                partial.setLength(0);
                session.receive(text);
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            session.endConnection("sidecar connection closed (" + statusCode + "): " + reason);
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            session.endConnection("sidecar connection error: " + error);
        }
    }
}
