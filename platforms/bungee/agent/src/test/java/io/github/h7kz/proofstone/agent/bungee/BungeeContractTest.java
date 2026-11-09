package io.github.h7kz.proofstone.agent.bungee;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.h7kz.proofstone.protocol.testing.GoldenMatcher;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.PlayerDisconnectEvent;
import net.md_5.bungee.api.event.ServerSwitchEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Replays every proxy-family golden case (proofstone-protocol/golden, T-M7-03) against the agent over HTTP, with a
 * {@link MockProxy} in place of BungeeCord. This checks the wire contract and what the agent asks of the BungeeCord API,
 * not BungeeCord itself: the real-server replay is {@code BungeeCordAgentIT}. The golden directory comes from the
 * {@code proofstone.golden} system property (build.gradle.kts).
 */
class BungeeContractTest {

    private static final File GOLDEN = new File(System.getProperty("proofstone.golden"));
    private static final UUID ALEX = UUID.fromString("8a2c1f0e-6b1d-4f7e-9c3a-2d5e7b9a1c40");

    private MockProxy mock;
    private BungeeAgent agent;
    private AgentClient http;

    @BeforeEach
    void start() throws IOException {
        mock = new MockProxy().server("lobby", 25565, true).server("survival", 25566, true);
        mock.player("Alex", ALEX, "lobby");
        agent = BungeeAgent.start(mock.host, "0.1.0",
                Map.of("PROOFSTONE_AGENT_TOKEN", AgentClient.TOKEN, "PROOFSTONE_AGENT_PORT", "0"));
        http = new AgentClient(agent.port());
    }

    @AfterEach
    void stop() {
        agent.close();
    }

    @Test
    void healthMatchesTheProxyGolden() throws IOException {
        JsonNode golden = read("health/proxy-ok");
        // The golden lists the tokens every proxy agent must have. This agent answers for more (command, log), so the
        // test checks the golden's tokens are all there and then compares the rest with the golden's own shape.
        List<String> expected = new ArrayList<>();
        golden.get("body").get("data").get("capabilities").forEach(token -> expected.add(token.asText()));
        assertThat(BungeeAgent.capabilities()).containsAll(expected).doesNotContain("events");
        ArrayNode all = ((ObjectNode) golden.get("body").get("data")).putArray("capabilities");
        BungeeAgent.capabilities().forEach(all::add);
        // The golden's platform is Velocity's: this agent's own id is bungeecord (spec section 2).
        ((ObjectNode) golden.get("body").get("data")).put("platform", "bungeecord");
        replay("health/proxy-ok", golden);
    }

    @Test
    void healthReportsTheProxyVersionAsMcVersion() throws IOException {
        JsonNode data = http.get("/v1/health").json().get("data");
        assertThat(data.get("platform").asText()).isEqualTo("bungeecord");
        assertThat(data.get("mcVersion").asText()).isEqualTo("git:BungeeCord-Bootstrap:1.21-R0.4-SNAPSHOT:mock:2111");
        assertThat(data.get("protocol").asText()).isEqualTo("1.2");
    }

    @Test
    void unauthorisedMatchesTheGolden() throws IOException {
        replay("health/unauthorised");
    }

    @Test
    void commandMatchesTheGoldenAndRunsThroughTheProxyPluginManager() throws IOException {
        replay("command/reply");
        assertThat(mock.commandsRun).containsExactly("version");
    }

    @Test
    void commandRepliesWithWhatTheProxyPrinted() throws IOException {
        mock.command("glist", (sender, args) -> sender.sendMessage("§6Total §fplayers online: 1"));
        JsonNode data = http.post("/v1/command", "{\"command\":\"glist\"}").json().get("data");
        // Legacy colour codes are stripped, like the console's plain text.
        assertThat(data.get("raw").asText()).isEqualTo("Total players online: 1");
    }

    @Test
    void commandOutputFromComponentsIsPlainTextOneLinePerMessage() throws IOException {
        mock.command("two", (sender, args) -> {
            sender.sendMessage(new net.md_5.bungee.api.chat.ComponentBuilder("first ")
                    .color(net.md_5.bungee.api.ChatColor.RED).append("part").create());
            sender.sendMessages("second", "third");
        });
        JsonNode data = http.post("/v1/command", "{\"command\":\"two\"}").json().get("data");
        assertThat(data.get("raw").asText()).isEqualTo("first part\nsecond\nthird");
    }

    @Test
    void commandArgumentsReachTheCommand() throws IOException {
        mock.command("send", (sender, args) -> { });
        http.post("/v1/command", "{\"command\":\"send Alex survival\"}");
        assertThat(mock.commandsRun).contains("send Alex survival");
    }

    @Test
    void aCommandTheProxyDoesNotKnowIsAnErrorNotAnEmptyReply() throws IOException {
        AgentClient.Reply reply = http.post("/v1/command", "{\"command\":\"nonsense\"}");
        assertThat(reply.status).isEqualTo(400);
        assertThat(reply.errorCode()).isEqualTo("INVALID_REQUEST");
        assertThat(reply.json().get("error").get("message").asText()).contains("nonsense");
    }

    @Test
    void theCommandSenderHoldsEveryPermissionAndIsNotAPlayer() throws IOException {
        mock.command("who", (sender, args) -> sender.sendMessage(sender.getName() + " "
                + sender.hasPermission("bungeecord.command.end") + " " + (sender instanceof ProxiedPlayer)));
        mock.plugins.registerCommand(mock.host, new net.md_5.bungee.api.plugin.Command("guarded", "some.permission") {
            @Override
            public void execute(net.md_5.bungee.api.CommandSender sender, String[] args) {
                sender.sendMessage("allowed");
            }
        });
        assertThat(http.post("/v1/command", "{\"command\":\"who\"}").json().get("data").get("raw").asText())
                .isEqualTo("CONSOLE true false");
        assertThat(http.post("/v1/command", "{\"command\":\"guarded\"}").json().get("data").get("raw").asText())
                .isEqualTo("allowed");
    }

    @Test
    void aStalledProxyAnswersTimeoutNeverAPartialResult() throws IOException {
        mock.schedulerStalls = true;
        AgentClient.Reply reply = http.post("/v1/command", "{\"command\":\"version\",\"timeoutMs\":200}");
        assertThat(reply.status).isEqualTo(504);
        assertThat(reply.errorCode()).isEqualTo("TIMEOUT");
        assertThat(mock.commandsRun).as("a stalled proxy ran nothing").isEmpty();
    }

    @Test
    void readsRunOnTheBungeeSchedulerNotOnTheHttpThread() throws IOException {
        int before = mock.tasksScheduled;
        http.get("/v1/proxy/servers");
        http.get("/v1/players/Alex");
        assertThat(mock.tasksScheduled - before).isEqualTo(2);
    }

    @Test
    void serversMatchTheGolden() throws IOException {
        replay("proxy/servers");
    }

    @Test
    void oneServerMatchesTheGolden() throws IOException {
        replay("proxy/server-get");
    }

    @Test
    void anUnknownServerMatchesTheGolden() throws IOException {
        replay("proxy/server-not-found");
    }

    @Test
    void aServerThatDoesNotAnswerThePingIsOffline() throws IOException {
        mock.server("dead", 1, false);
        JsonNode servers = http.get("/v1/proxy/servers").json().get("data").get("servers");
        assertThat(servers).hasSize(3);
        JsonNode dead = servers.get(0);
        assertThat(dead.get("name").asText()).isEqualTo("dead");
        assertThat(dead.get("online").asBoolean()).isFalse();
        assertThat(dead.get("address").asText()).isEqualTo("127.0.0.1:1");
        assertThat(servers.get(1).get("online").asBoolean()).isTrue();
    }

    @Test
    void aPlayerOnTheFirstServerHasNoPreviousServer() throws IOException {
        replay("players/proxy-first-join");
    }

    @Test
    void aSwitchedPlayerMatchesTheGolden() throws IOException {
        replay("players/proxy-first-join");
        mock.players.clear();
        ProxiedPlayer alex = mock.player("Alex", ALEX, "survival");
        mock.plugins.callEvent(new ServerSwitchEvent(alex, mock.server("lobby")));
        replay("players/proxy-switched");
    }

    @Test
    void aPlayerIsForgottenWhenTheyLeave() throws IOException {
        mock.players.clear();
        ProxiedPlayer alex = mock.player("Alex", ALEX, "survival");
        mock.plugins.callEvent(new ServerSwitchEvent(alex, mock.server("lobby")));
        mock.plugins.callEvent(new PlayerDisconnectEvent(alex));
        mock.players.clear();
        mock.player("Alex", ALEX, "lobby");
        assertThat(http.get("/v1/players/Alex").json().get("data").get("proxy").get("previousServer").isNull())
                .isTrue();
    }

    @Test
    void aFirstJoinSwitchEventWithNoSourceLeavesNoPreviousServer() throws IOException {
        ProxiedPlayer alex = mock.players.get(0);
        mock.plugins.callEvent(new ServerSwitchEvent(alex, null));
        assertThat(http.get("/v1/players/Alex").json().get("data").get("proxy").get("previousServer").isNull())
                .isTrue();
    }

    @Test
    void anUnknownPlayerMatchesTheGolden() throws IOException {
        replay("players/not-found");
    }

    @Test
    void aPlayerNotYetOnAnyServerIsNotFoundYet() throws IOException {
        mock.player("Early", UUID.fromString("00000000-0000-4000-8000-000000000001"), null);
        AgentClient.Reply reply = http.get("/v1/players/Early");
        assertThat(reply.status).isEqualTo(404);
        assertThat(reply.errorCode()).isEqualTo("PLAYER_NOT_FOUND");
    }

    @Test
    void playersListsTheNamesOnTheProxy() throws IOException {
        JsonNode players = http.get("/v1/players").json().get("data").get("players");
        assertThat(players).hasSize(1);
        assertThat(players.get(0).asText()).isEqualTo("Alex");
    }

    @Test
    void logMatchesTheGoldenWithAStackTrace() throws IOException {
        // The proxy's own logger has no parent: a record on it, or on a plugin logger under it, reaches the agent only
        // because the buffer is attached to that logger and not to the JUL root.
        mock.logger.log(Level.WARNING, "a server went away", new IllegalStateException("boom"));
        replay("log/read");
        JsonNode record = http.get("/v1/log?since=0&minLevel=WARN").json().get("data").get("records").get(0);
        assertThat(record.get("thrown").asText()).contains("IllegalStateException: boom").contains("at ");
    }

    @Test
    void aPluginLoggerRecordIsCapturedToo() throws IOException {
        mock.host.getLogger().log(Level.SEVERE, "from a plugin");
        JsonNode records = http.get("/v1/log?since=0&minLevel=ERROR").json().get("data").get("records");
        assertThat(records).hasSize(1);
        assertThat(records.get(0).get("level").asText()).isEqualTo("ERROR");
        assertThat(records.get(0).get("message").asText()).endsWith("from a plugin");
    }

    @Test
    void gameStateEndpointsAreNotServedByAProxy() throws IOException {
        for (String path : List.of("/v1/world/block?world=w&x=0&y=0&z=0", "/v1/ticks", "/v1/snapshot",
                "/v1/plugins/x/config?path=a")) {
            AgentClient.Reply reply = http.get(path);
            assertThat(reply.status).as(path).isEqualTo(404);
            assertThat(reply.errorCode()).as(path).isEqualTo("NOT_FOUND");
        }
        AgentClient.Reply give = http.post("/v1/players/Alex/give", "{\"type\":\"minecraft:stone\"}");
        assertThat(give.status).isEqualTo(404);
        assertThat(give.errorCode()).isEqualTo("NOT_FOUND");
    }

    private void replay(String name) throws IOException {
        replay(name, read(name));
    }

    private void replay(String name, JsonNode expected) throws IOException {
        JsonNode request = AgentClient.JSON.readTree(new File(GOLDEN, name + ".request.json"));
        String authorization = request.has("auth") && !request.get("auth").asBoolean()
                ? null : "Bearer " + AgentClient.TOKEN;
        String body = request.has("body") ? request.get("body").toString() : null;
        AgentClient.Reply actual = http.send(request.get("method").asText(), request.get("path").asText(),
                authorization, body);
        assertThat(actual.status).as(name + " status").isEqualTo(expected.get("status").asInt());
        assertThat(GoldenMatcher.mismatches(expected.get("body").toString(), actual.body))
                .as(name + " against the golden").isEmpty();
    }

    private static JsonNode read(String name) throws IOException {
        return AgentClient.JSON.readTree(new File(GOLDEN, name + ".response.json"));
    }
}
