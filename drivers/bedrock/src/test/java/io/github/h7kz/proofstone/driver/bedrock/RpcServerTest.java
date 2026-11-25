package io.github.h7kz.proofstone.driver.bedrock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.h7kz.proofstone.core.driver.ChatMessage;
import io.github.h7kz.proofstone.core.driver.ClientDriverException;
import io.github.h7kz.proofstone.core.driver.ClientSession;
import io.github.h7kz.proofstone.core.driver.ConnectSpec;
import io.github.h7kz.proofstone.core.driver.JoinTimeoutException;
import io.github.h7kz.proofstone.core.driver.SlotStack;
import io.github.h7kz.proofstone.core.driver.bedrock.BedrockDriver;
import io.github.h7kz.proofstone.core.driver.bedrock.HostTransport;
import io.github.h7kz.proofstone.core.driver.bedrock.rpc.BedrockRpc;
import io.github.h7kz.proofstone.core.driver.bedrock.rpc.BedrockRpc.Position;
import io.github.h7kz.proofstone.core.driver.bedrock.rpc.BedrockRpc.Slot;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Driver RPC v1 end to end over real HTTP on loopback: the harness-side {@link BedrockDriver} against this module's
 * {@link RpcServer}, with scripted connections in place of the network. Both sides compile against {@link BedrockRpc}.
 */
class RpcServerTest {

    private static final String TOKEN = "t0ken";

    static final class FakeConnection implements RpcServer.Connection {
        final String name;
        final String xuid;
        final SessionState state = new SessionState();
        final List<String> sent = new ArrayList<>();
        boolean closed;

        FakeConnection(String name, String xuid) {
            this.name = name;
            this.xuid = xuid;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public String xuid() {
            return xuid;
        }

        @Override
        public SessionState state() {
            return state;
        }

        @Override
        public void chat(String message) {
            sent.add("chat " + message);
        }

        @Override
        public void command(String command) {
            sent.add("command " + command);
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    private final HttpClient http = HttpClient.newHttpClient();
    private final List<FakeConnection> connections = new ArrayList<>();
    private RpcServer server;

    /** Reaches the server by real HTTP, as the exec-curl transport does from inside the container. */
    private HostTransport transport(String token) {
        return (method, path, body, timeout) -> {
            try {
                HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.port() + path))
                        .timeout(timeout).header("Authorization", "Bearer " + token)
                        .header(BedrockRpc.REQUEST_ID_HEADER, "req-1")
                        .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
                return http.send(request.build(), HttpResponse.BodyHandlers.ofString()).body();
            } catch (IOException e) {
                throw new ClientDriverException("unreachable", e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ClientDriverException("interrupted", e);
            }
        };
    }

    @BeforeEach
    void start() throws IOException {
        server = new RpcServer(0, TOKEN, (id, request) -> {
            switch (request.name()) {
                case "Stall" -> throw new HostSession.HostError(BedrockRpc.JOIN_TIMEOUT, "no progress past this stage",
                        "LOGIN_SENT", "NetworkSettingsPacket");
                case "Kicked" -> throw new HostSession.HostError(BedrockRpc.DISCONNECTED,
                        "Please log into Xbox to join this server.", "LOGIN_SENT", "DisconnectPacket");
                default -> {
                    FakeConnection connection = new FakeConnection(request.name(), request.xuid());
                    connection.state.position(new Position(1.5, 64, -2));
                    connections.add(connection);
                    return connection;
                }
            }
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.close();
    }

    private ClientSession join(String name) {
        return new BedrockDriver(transport(TOKEN)).connect(new ConnectSpec("server", 19132, name, Duration.ofSeconds(5)));
    }

    private String raw(String method, String path, String body, String token) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.port() + path))
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
        return response.statusCode() + " " + response.body();
    }

    @Test
    void theDriverJoinsReadsAndLeavesThroughTheHost() {
        ClientSession session = join("Alex");
        FakeConnection connection = connections.get(0);
        assertThat(connection.xuid).matches("[1-9][0-9]{15}");

        session.chat("hello");
        session.command("give Alex diamond 3");
        assertThat(connection.sent).containsExactly("chat hello", "command give Alex diamond 3");

        connection.state.message("Given 3 [Diamond] to Alex", true);
        assertThat(session.awaitMessage(Pattern.compile("Given \\d"), Duration.ofSeconds(2)))
                .isEqualTo(new ChatMessage("Given 3 [Diamond] to Alex", true));

        connection.state.inventory(List.of(new Slot(0, "minecraft:diamond", 318, 3), new Slot(1, "minecraft:air", 0, 0)));
        assertThat(session.inventory().slots()).containsExactly(new SlotStack(318, 3), SlotStack.EMPTY);
        assertThat(session.position()).containsExactly(1.5, 64.0, -2.0);
        assertThat(session.transcript()).isNotNull();

        session.close();
        assertThat(connection.closed).isTrue();
    }

    @Test
    void aMessageThatBeatsTheCallIsNotLostAndIsConsumedOnce() {
        ClientSession session = join("Alex");
        connections.get(0).state.message("one", true);
        assertThat(session.awaitMessage(Pattern.compile("one"), Duration.ofSeconds(2)).text()).isEqualTo("one");
        assertThatThrownBy(() -> session.awaitMessage(Pattern.compile("one"), Duration.ofMillis(50)))
                .isInstanceOf(ClientDriverException.class).hasMessageContaining("TIMEOUT");
    }

    @Test
    void anAwaitTimeoutIsAnErrorWithTheUnconsumedLinesNeverAPartialResult() {
        ClientSession session = join("Alex");
        connections.get(0).state.message("other line", true);
        assertThatThrownBy(() -> session.awaitMessage(Pattern.compile("never"), Duration.ofMillis(50)))
                .isInstanceOf(ClientDriverException.class).hasMessageContaining("TIMEOUT")
                .hasMessageContaining("[other line]");
    }

    @Test
    void aSessionThatEndedAnswersItsFailureOnReadsAndAwaits() {
        ClientSession session = join("Alex");
        connections.get(0).state.fail(BedrockRpc.DISCONNECTED, "the connection closed: Kicked");
        assertThatThrownBy(session::inventory).hasMessageContaining("Kicked");
        assertThatThrownBy(session::position).hasMessageContaining("Kicked");
        assertThatThrownBy(() -> session.awaitMessage(Pattern.compile("x"), Duration.ofSeconds(5)))
                .hasMessageContaining("DISCONNECTED").hasMessageContaining("Kicked");
    }

    @Test
    void anUnknownTransportIsABadRequestAndNetherNetIsAccepted() throws Exception {
        String body = "{\"host\":\"server\",\"port\":19132,\"name\":\"Alex\",\"xuid\":\"2535400000000999\",\"joinTimeoutMillis\":5000,\"transport\":\"%s\"}";
        assertThat(raw("POST", BedrockRpc.SESSIONS, body.formatted("quic"), TOKEN)).startsWith("400 ").contains("BAD_REQUEST").contains("quic");
        assertThat(raw("POST", BedrockRpc.SESSIONS, body.formatted("nethernet"), TOKEN)).startsWith("200 ");
    }

    @Test
    void aJoinStallIsAJoinTimeoutNamingTheStage() {
        assertThatThrownBy(() -> join("Stall")).isInstanceOf(JoinTimeoutException.class)
                .hasMessageStartingWith("timed out joining").hasMessageContaining("at stage LOGIN_SENT")
                .satisfies(e -> assertThat(((JoinTimeoutException) e).lastReceived()).isEqualTo("NetworkSettingsPacket"));
    }

    @Test
    void aServersRefusalIsItsOwnErrorQuotingTheText() {
        assertThatThrownBy(() -> join("Kicked")).isNotInstanceOf(JoinTimeoutException.class)
                .hasMessageContaining("Please log into Xbox to join this server.");
    }

    @Test
    void theTokenIsRequired() throws Exception {
        assertThat(raw("GET", BedrockRpc.HEALTH, null, null)).startsWith("401 ").contains("UNAUTHORIZED");
        assertThat(raw("GET", BedrockRpc.HEALTH, null, "wrong")).startsWith("401 ");
        assertThat(raw("GET", BedrockRpc.HEALTH, null, TOKEN)).startsWith("200 ")
                .contains("\"rpc\":1", "\"protocol\":2225", "\"gameVersion\":\"1.26.60\"");
    }

    @Test
    void aRequestWithAnUnknownFieldOrBadValuesIsRefused() throws Exception {
        String ok = "{\"host\":\"s\",\"port\":19132,\"name\":\"Alex\",\"xuid\":\"2535400000000001\",\"joinTimeoutMillis\":1000";
        assertThat(raw("POST", BedrockRpc.SESSIONS, ok + ",\"extra\":1}", TOKEN)).startsWith("400 ").contains("BAD_REQUEST");
        assertThat(raw("POST", BedrockRpc.SESSIONS, ok.replace("2535400000000001", "") + "}", TOKEN)).startsWith("400 ")
                .contains("XUID");
        assertThat(raw("POST", BedrockRpc.SESSIONS, ok.replace("Alex", "a b") + "}", TOKEN)).startsWith("400 ")
                .contains("1 to 15");
        assertThat(raw("POST", BedrockRpc.SESSIONS, ok.replace("1000", "0") + "}", TOKEN)).startsWith("400 ");
        assertThat(raw("POST", BedrockRpc.SESSIONS, "not json", TOKEN)).startsWith("400 ");
        assertThat(connections).isEmpty();
    }

    @Test
    void aXuidIsUniqueWhileItsSessionIsLiveAndFreeAfterwards() throws Exception {
        String request = "{\"host\":\"s\",\"port\":19132,\"name\":\"Alex\",\"xuid\":\"2535400000000001\",\"joinTimeoutMillis\":1000}";
        String first = raw("POST", BedrockRpc.SESSIONS, request, TOKEN);
        assertThat(first).startsWith("200 ").contains("\".Alex\"");
        assertThat(raw("POST", BedrockRpc.SESSIONS, request.replace("Alex", "Steve"), TOKEN)).startsWith("400 ")
                .contains("already used");
        String id = first.replaceAll(".*\"sessionId\":\"([^\"]+)\".*", "$1");
        assertThat(raw("DELETE", BedrockRpc.SESSIONS + "/" + id, null, TOKEN)).startsWith("200 ");
        assertThat(raw("POST", BedrockRpc.SESSIONS, request.replace("Alex", "Steve"), TOKEN)).startsWith("200 ");
    }

    @Test
    void aFailedJoinReleasesItsXuid() throws Exception {
        String request = "{\"host\":\"s\",\"port\":19132,\"name\":\"Kicked\",\"xuid\":\"2535400000000009\",\"joinTimeoutMillis\":1000}";
        assertThat(raw("POST", BedrockRpc.SESSIONS, request, TOKEN)).startsWith("409 ");
        assertThat(raw("POST", BedrockRpc.SESSIONS, request, TOKEN)).startsWith("409 ");
        assertThat(raw("POST", BedrockRpc.SESSIONS, request.replace("Kicked", "Stall"), TOKEN)).startsWith("408 ")
                .contains("JOIN_TIMEOUT", "\"stage\":\"LOGIN_SENT\"");
    }

    @Test
    void unknownSessionsAndPathsAreNotFoundAndAnAwaitBodyIsChecked() throws Exception {
        assertThat(raw("GET", BedrockRpc.SESSIONS + "/nope/inventory", null, TOKEN)).startsWith("404 ").contains("NOT_FOUND");
        assertThat(raw("GET", BedrockRpc.BASE + "/other", null, TOKEN)).startsWith("404 ");
        ClientSession session = join("Alex");
        String id = connections.get(0).xuid;
        assertThat(id).isNotBlank();
        String sessions = raw("POST", BedrockRpc.SESSIONS, "{\"host\":\"s\",\"port\":19132,\"name\":\"Bo\",\"xuid\":\"2535400000000002\",\"joinTimeoutMillis\":1000}", TOKEN);
        String sid = sessions.replaceAll(".*\"sessionId\":\"([^\"]+)\".*", "$1");
        String path = BedrockRpc.SESSIONS + "/" + sid;
        assertThat(raw("POST", path + "/await-message", "{\"pattern\":\"(\",\"timeoutMillis\":10}", TOKEN)).startsWith("400 ");
        assertThat(raw("POST", path + "/await-message", "{\"pattern\":\"a\",\"timeoutMillis\":0}", TOKEN)).startsWith("400 ");
        assertThat(raw("POST", path + "/chat", "{\"message\":\"\"}", TOKEN)).startsWith("400 ");
        assertThat(raw("GET", path + "/messages?since=x", null, TOKEN)).startsWith("400 ");
        assertThat(raw("GET", path + "/messages?since=0", null, TOKEN)).startsWith("200 ");
        session.close();
    }
}
