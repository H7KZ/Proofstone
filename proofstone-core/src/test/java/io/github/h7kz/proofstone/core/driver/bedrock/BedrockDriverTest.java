package io.github.h7kz.proofstone.core.driver.bedrock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.h7kz.proofstone.api.BlockPos;
import io.github.h7kz.proofstone.core.driver.ChatMessage;
import io.github.h7kz.proofstone.core.driver.ClientDriverException;
import io.github.h7kz.proofstone.core.driver.ClientSession;
import io.github.h7kz.proofstone.core.driver.ConnectSpec;
import io.github.h7kz.proofstone.core.driver.Edition;
import io.github.h7kz.proofstone.api.Face;
import io.github.h7kz.proofstone.core.driver.JoinTimeoutException;
import io.github.h7kz.proofstone.core.driver.SlotStack;
import io.github.h7kz.proofstone.core.driver.WindowView;
import io.github.h7kz.proofstone.core.driver.bedrock.rpc.BedrockRpc;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** The driver against a scripted host: the RPC contract from the harness side (ADR-0018, client-driver spec section 6). */
class BedrockDriverTest {

    private static final String HEALTH = ok("{\"rpc\":1,\"protocol\":2225,\"gameVersion\":\"1.26.60\"}");
    private static final ConnectSpec SPEC = new ConnectSpec("server", 19132, "Alex", Duration.ofSeconds(30));

    /** Answers each call from a queue and records it. */
    static final class Script implements HostTransport {
        final Queue<String> replies = new ArrayDeque<>();
        final List<String> calls = new ArrayList<>();
        final List<Duration> timeouts = new ArrayList<>();

        Script reply(String... bodies) {
            replies.addAll(List.of(bodies));
            return this;
        }

        @Override
        public String call(String method, String path, String body, Duration timeout) {
            calls.add(method + " " + path + (body == null ? "" : " " + body));
            timeouts.add(timeout);
            String next = replies.poll();
            if (next == null) {
                throw new AssertionError("no scripted reply for " + method + " " + path);
            }
            return next;
        }
    }

    private static String ok(String data) {
        return "{\"ok\":true,\"data\":" + data + "}";
    }

    private static String error(String code, String message, String stage, String lastPacket) {
        return "{\"ok\":false,\"error\":{\"code\":\"" + code + "\",\"message\":\"" + message + "\""
                + (stage == null ? "" : ",\"stage\":\"" + stage + "\"")
                + (lastPacket == null ? "" : ",\"lastPacket\":\"" + lastPacket + "\"") + "}}";
    }

    private static ClientSession joined(Script script) {
        script.reply(HEALTH, ok("{\"sessionId\":\"s1\",\"name\":\".Alex\"}"));
        return new BedrockDriver(script).connect(SPEC);
    }

    @Test
    void speaksBedrockOnly() {
        BedrockDriver driver = new BedrockDriver(new Script());
        assertThat(driver.id()).isEqualTo("bedrock");
        assertThat(driver.edition()).isEqualTo(Edition.BEDROCK);
        assertThat(driver.protocolVersion()).isEqualTo("1.26.60");
        assertThat(BedrockRpc.PROTOCOL).isEqualTo(2225);
    }

    @Test
    void aRakNetRequestCarriesNoTransportFieldAndANetherNetOneNamesIt() {
        Script raknet = new Script();
        joined(raknet);
        assertThat(raknet.calls.get(raknet.calls.size() - 1)).doesNotContain("transport");
        Script nethernet = new Script().reply(HEALTH, ok("{\"sessionId\":\"s1\",\"name\":\".Alex\"}"));
        new BedrockDriver(nethernet, BedrockRpc.TRANSPORT_NETHERNET).connect(SPEC);
        assertThat(nethernet.calls.get(nethernet.calls.size() - 1)).contains("\"transport\":\"nethernet\"");
    }

    @Test
    void anUnknownTransportIsRefusedAtConstruction() {
        assertThatThrownBy(() -> new BedrockDriver(new Script(), "webtransport"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("webtransport");
    }

    @Test
    void refusesAJavaEndpointBeforeAnyCall() {
        Script script = new Script();
        assertThatThrownBy(() -> new BedrockDriver(script).connect(SPEC, Edition.JAVA))
                .isInstanceOf(ClientDriverException.class)
                .hasMessageContaining("BEDROCK").hasMessageContaining("JAVA").hasMessageContaining("server:19132");
        assertThat(script.calls).isEmpty();
    }

    @Test
    void refusesABridgedJoin() {
        ConnectSpec bridged = new ConnectSpec("server", 19132, "Alex", Duration.ofSeconds(5),
                io.github.h7kz.proofstone.core.driver.ResourcePackPolicy.NONE, true);
        assertThatThrownBy(() -> new BedrockDriver(new Script()).connect(bridged))
                .isInstanceOf(ClientDriverException.class).hasMessageContaining("ADR-0018");
    }

    @Test
    void joinSendsANumericUniqueXuidAndTheTimeout() {
        Script script = new Script();
        Set<String> xuids = new HashSet<>();
        BedrockDriver driver = new BedrockDriver(script);
        for (int i = 0; i < 50; i++) {
            script.reply(HEALTH, ok("{\"sessionId\":\"s" + i + "\",\"name\":\".Alex\"}"));
            driver.connect(SPEC);
            String body = script.calls.get(script.calls.size() - 1);
            assertThat(body).startsWith("POST /rpc/v1/sessions {").contains("\"host\":\"server\"", "\"port\":19132",
                    "\"name\":\"Alex\"", "\"joinTimeoutMillis\":30000");
            String xuid = body.replaceAll(".*\"xuid\":\"([^\"]*)\".*", "$1");
            assertThat(xuid).matches("[1-9][0-9]{15}");
            assertThat(xuids.add(xuid)).as("xuid " + xuid + " is new").isTrue();
        }
        // The transport may take longer than the host: the host's own timeout reply must arrive first.
        assertThat(script.timeouts.get(script.timeouts.size() - 1)).isGreaterThan(Duration.ofSeconds(30));
    }

    @Test
    void aBadPlayerNameIsRefusedBeforeAnyCall() {
        Script script = new Script();
        ConnectSpec spec = new ConnectSpec("server", 19132, "Not Valid!", Duration.ofSeconds(5));
        assertThatThrownBy(() -> new BedrockDriver(script).connect(spec)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("1 to 15");
        assertThat(script.calls).isEmpty();
    }

    @Test
    void aJoinTimeoutNamesTheStalledStage() {
        Script script = new Script().reply(HEALTH, error("JOIN_TIMEOUT", "no progress past this stage",
                "ENCRYPTION_HANDSHAKE", "ServerToClientHandshakePacket"));
        assertThatThrownBy(() -> new BedrockDriver(script).connect(SPEC)).isInstanceOf(JoinTimeoutException.class)
                .hasMessageStartingWith("timed out joining server:19132 as Alex at stage ENCRYPTION_HANDSHAKE")
                .satisfies(e -> {
                    JoinTimeoutException timeout = (JoinTimeoutException) e;
                    assertThat(timeout.stage()).isEqualTo("ENCRYPTION_HANDSHAKE");
                    assertThat(timeout.player()).isEqualTo("Alex");
                    assertThat(timeout.lastReceived()).isEqualTo("ServerToClientHandshakePacket");
                    assertThat(timeout.protocolOffered()).contains("2225");
                });
    }

    @Test
    void everyStageOfTheAdrIsAKnownStageInOrder() {
        assertThat(BedrockRpc.JoinStage.values()).extracting(Enum::name).containsExactly("RAKNET_CONNECT",
                "SIGNALLING", "NETHERNET_CONNECT", "NETWORK_SETTINGS", "LOGIN_SENT", "ENCRYPTION_HANDSHAKE", "RESOURCE_PACKS", "START_GAME", "SPAWN_STATUS");
    }

    @Test
    void aServerRefusalIsQuotedAndNeverATimeout() {
        Script script = new Script().reply(HEALTH, error("DISCONNECTED", "Please log into Xbox to join this server.",
                "LOGIN_SENT", "DisconnectPacket"));
        assertThatThrownBy(() -> new BedrockDriver(script).connect(SPEC))
                .isNotInstanceOf(JoinTimeoutException.class).isInstanceOf(ClientDriverException.class)
                .hasMessageContaining("Please log into Xbox to join this server.");
    }

    @Test
    void aHostOnAnotherProtocolIsAnError() {
        Script script = new Script().reply(ok("{\"rpc\":1,\"protocol\":2200,\"gameVersion\":\"1.26.0\"}"));
        assertThatThrownBy(() -> new BedrockDriver(script).connect(SPEC)).isInstanceOf(ClientDriverException.class)
                .hasMessageContaining("2200").hasMessageContaining("2225");
        Script otherRpc = new Script().reply(ok("{\"rpc\":2,\"protocol\":2225,\"gameVersion\":\"1.26.60\"}"));
        assertThatThrownBy(() -> new BedrockDriver(otherRpc).connect(SPEC)).hasMessageContaining("RPC 2");
    }

    @Test
    void anUnknownFieldInAReplyIsAnErrorNeverIgnored() {
        Script script = new Script().reply(ok("{\"rpc\":1,\"protocol\":2225,\"gameVersion\":\"1.26.60\",\"extra\":1}"));
        assertThatThrownBy(() -> new BedrockDriver(script).connect(SPEC)).isInstanceOf(ClientDriverException.class)
                .hasMessageContaining("not RPC v1");
        Script emptyData = new Script().reply(HEALTH, "{\"ok\":true}");
        assertThatThrownBy(() -> new BedrockDriver(emptyData).connect(SPEC)).hasMessageContaining("no data");
        Script garbage = new Script().reply("<html>");
        assertThatThrownBy(() -> new BedrockDriver(garbage).connect(SPEC)).hasMessageContaining("not RPC v1");
    }

    @Test
    void chatAndCommandPostTheirText() {
        Script script = new Script();
        ClientSession session = joined(script);
        script.reply(ok("{\"done\":true}"), ok("{\"done\":true}"));
        session.chat("hello \"world\"");
        session.command("give Alex diamond 3");
        assertThat(script.calls.subList(2, 4)).containsExactly(
                "POST /rpc/v1/sessions/s1/chat {\"message\":\"hello \\\"world\\\"\"}",
                "POST /rpc/v1/sessions/s1/command {\"command\":\"give Alex diamond 3\"}");
    }

    @Test
    void awaitMessageReturnsTheHostsMatchAndTimesOutLoudly() {
        Script script = new Script();
        ClientSession session = joined(script);
        script.reply(ok("{\"seq\":4,\"text\":\"Given 3 [Diamond]\",\"system\":true}"),
                error("TIMEOUT", "no message matching x within 1000 ms; unconsumed: [a]", null, null));
        assertThat(session.awaitMessage(Pattern.compile("Given \\d"), Duration.ofSeconds(2)))
                .isEqualTo(new ChatMessage("Given 3 [Diamond]", true));
        assertThat(script.calls.get(2)).isEqualTo(
                "POST /rpc/v1/sessions/s1/await-message {\"pattern\":\"Given \\\\d\",\"timeoutMillis\":2000}");
        assertThatThrownBy(() -> session.awaitMessage(Pattern.compile("x"), Duration.ofSeconds(1)))
                .isInstanceOf(ClientDriverException.class).hasMessageContaining("TIMEOUT").hasMessageContaining("unconsumed");
    }

    @Test
    void inventoryAndPositionAreTheServersView() {
        Script script = new Script();
        ClientSession session = joined(script);
        script.reply(ok("{\"slots\":[{\"slot\":0,\"id\":\"minecraft:diamond\",\"runtimeId\":318,\"count\":3},"
                + "{\"slot\":1,\"id\":\"minecraft:air\",\"runtimeId\":0,\"count\":0}]}"),
                ok("{\"x\":1.5,\"y\":64.0,\"z\":-2.25}"));
        WindowView inventory = session.inventory();
        assertThat(inventory.type()).isEqualTo("minecraft:inventory");
        assertThat(inventory.slots()).containsExactly(new SlotStack(318, 3), SlotStack.EMPTY);
        assertThat(inventory.itemViews()).hasSize(2);
        assertThat(inventory.itemViews().get(0).type()).isEqualTo("minecraft:diamond");
        assertThat(inventory.itemViews().get(0).amount()).isEqualTo(3);
        assertThat(inventory.itemViews().get(1).isEmpty()).isTrue();
        assertThat(session.position()).containsExactly(1.5, 64.0, -2.25);
    }

    @Test
    void anErrorReplyOfASessionCallNamesTheSessionAndTheServersText() {
        Script script = new Script();
        ClientSession session = joined(script);
        script.reply(error("DISCONNECTED", "the connection closed: Kicked", null, null));
        assertThatThrownBy(session::inventory).isInstanceOf(ClientDriverException.class)
                .hasMessageContaining(".Alex").hasMessageContaining("Kicked");
    }

    @Test
    void closeDeletesTheSessionOnce() {
        Script script = new Script();
        ClientSession session = joined(script);
        script.reply(ok("{\"done\":true}"));
        session.close();
        session.close();
        assertThat(script.calls.subList(2, script.calls.size())).containsExactly("DELETE /rpc/v1/sessions/s1");
    }

    @Test
    void outOfScopeOperationsFailLoudlyNamingTheEdition() {
        ClientSession session = joined(new Script());
        assertThatThrownBy(() -> session.useBlock(new BlockPos(0, 0, 0), Face.UP))
                .isInstanceOf(UnsupportedOperationException.class).hasMessageContaining("Bedrock").hasMessageContaining("ADR-0018");
        assertThatThrownBy(() -> session.placeBlock(new BlockPos(0, 0, 0), Face.UP))
                .isInstanceOf(UnsupportedOperationException.class).hasMessageContaining("Bedrock");
        assertThatThrownBy(() -> session.breakBlock(new BlockPos(0, 0, 0)))
                .isInstanceOf(UnsupportedOperationException.class).hasMessageContaining("Bedrock");
        assertThatThrownBy(() -> session.awaitWindow(Duration.ofSeconds(1))).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(session::experienceLevel).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> session.awaitServerSwitch(Duration.ofSeconds(1))).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(session::resourcePacks).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void theNameAndXuidRulesMatchFloodgate() {
        assertThat(BedrockRpc.requireName("A_b9")).isEqualTo("A_b9");
        for (String bad : new String[] {"", "Sixteen_chars_xx", "has space", "dot.name", null}) {
            assertThatThrownBy(() -> BedrockRpc.requireName(bad)).isInstanceOf(IllegalArgumentException.class);
        }
        assertThat(BedrockRpc.requireXuid("2535400000000001")).isEqualTo("2535400000000001");
        for (String bad : new String[] {"", "0", "012", "abc", "-5", "1".repeat(20), null}) {
            assertThatThrownBy(() -> BedrockRpc.requireXuid(bad)).isInstanceOf(IllegalArgumentException.class);
        }
    }
}
