package io.github.h7kz.proofstone.agent.neoforge;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.h7kz.proofstone.agent.log.LogBuffer;
import io.github.h7kz.proofstone.agent.server.AgentConfig;
import io.github.h7kz.proofstone.agent.vanilla.AgentClient;
import io.github.h7kz.proofstone.agent.vanilla.MockView;
import io.github.h7kz.proofstone.agent.vanilla.TickCounter;
import io.github.h7kz.proofstone.protocol.testing.GoldenMatcher;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.neoforged.bus.api.BusBuilder;
import net.neoforged.bus.api.IEventBus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Replays the golden cases a NeoForge server serves (proofstone-protocol/golden) against the agent over HTTP, with a
 * {@link MockView} in place of the game and NeoForge's real event bus. This checks the wire contract, the threading and
 * the probes, not the game: the real-server replay is {@code NeoForgeAgentIT}. Cases whose golden names a Bukkit or
 * Fabric value (the health platform, the join event, the snapshot's plugins, the mod list) are compared with that part
 * replaced by the NeoForge equivalent; the rest of each golden stands as written.
 */
class NeoForgeContractTest {

    private static final File GOLDEN = new File(System.getProperty("proofstone.golden"));

    private final IEventBus bus = BusBuilder.builder().build();
    private NeoForgeMockView mock;
    private LogBuffer logs;
    private TickCounter ticks;
    private NeoForgeAgent agent;
    private AgentClient http;

    @BeforeEach
    void start() throws IOException {
        mock = new NeoForgeMockView();
        logs = LogBuffer.attach();
        ticks = new TickCounter();
        agent = NeoForgeAgent.start(new AgentConfig(AgentClient.TOKEN, 0), mock::execute, mock,
                new NeoForgeEventProbes(bus, ticks::current), logs, ticks, "0.1.0", "26.3");
        http = new AgentClient(agent.port());
    }

    @AfterEach
    void stop() {
        agent.close();
        logs.detach();
        mock.serverThread.shutdownNow();
    }

    @Test
    void healthMatchesTheGoldenAsANeoForgeAgent() throws IOException {
        JsonNode golden = read("health/ok.response.json");
        ObjectNode data = (ObjectNode) golden.get("body").get("data");
        data.put("platform", "neoforge");
        ArrayNode tokens = data.putArray("capabilities");
        NeoForgeAgent.capabilities().forEach(tokens::add);
        replay("health/ok", golden);
        assertThat(NeoForgeAgent.capabilities()).contains("mods").doesNotContain("config", "reload", "datapacks");
        assertThat(http.get("/v1/health").json().get("data").get("mcVersion").asText()).isEqualTo("26.3");
    }

    @Test
    void unauthorisedMatchesTheGolden() throws IOException {
        replay("health/unauthorised");
    }

    @Test
    void modsMatchTheGoldenWithTheAgentAsTheOnlyMod() throws IOException {
        // The golden lists what the fabric@26.3 row loads (fabric-api and the Fabric agent). The neoforge@26.3 row puts
        // only the agent in /mods, and a NeoForge mod id cannot hold '-': the shape stays, the list is the NeoForge one.
        JsonNode golden = read("mods/list.response.json");
        ArrayNode mods = (ArrayNode) golden.get("body").get("data").get("mods");
        ObjectNode agentEntry = ((ObjectNode) mods.get(1)).deepCopy();
        agentEntry.put("id", ProofstoneNeoForgeAgent.MOD_ID);
        mods.removeAll();
        mods.add(agentEntry);
        replay("mods/list", golden);
    }

    @Test
    void anUnknownPlayerMatchesTheGolden() throws IOException {
        replay("players/not-found");
    }

    @Test
    void theFixturePlayerMatchesTheGoldensThroughResetGiveAndRead() throws IOException {
        assertThat(http.post("/v1/players/Alex/reset", null).status).isEqualTo(200);
        replay("players/empty-inventory");
        replay("players/give-enchanted-sword");
        replay("players/read-after-give");
    }

    @Test
    void theSnapshotMatchesTheGoldenWithNoPlugins() throws IOException {
        http.post("/v1/players/Alex/reset", null);
        replay("players/give-enchanted-sword");
        JsonNode golden = read("snapshot/read.response.json");
        // A NeoForge server has no plugins: the list is there and empty (snapshot.json requires it).
        ((ObjectNode) golden.get("body").get("data")).putArray("plugins");
        replay("snapshot/read", golden);
    }

    @Test
    void commandMatchesTheGoldenAndUnknownCommandsAreErrors() throws IOException {
        replay("command/reply");
        assertThat(mock.commandsRun).containsExactly("version");
        AgentClient.Reply unknown = http.post("/v1/command", "{\"command\":\"nonsense\"}");
        assertThat(unknown.status).isEqualTo(400);
        assertThat(unknown.errorCode()).isEqualTo("INVALID_REQUEST");
    }

    @Test
    void blocksMatchTheGoldens() throws IOException {
        replay("world/block-get");
        replay("world/block-put");
    }

    @Test
    void everyGameReadAndWriteRunsOnTheServerThread() throws IOException {
        http.get("/v1/players");
        http.get("/v1/players/Alex");
        http.post("/v1/players/Alex/give", "{\"type\":\"minecraft:stone\",\"amount\":1}");
        http.post("/v1/players/Alex/reset", null);
        http.send("PATCH", "/v1/players/Alex", "Bearer " + AgentClient.TOKEN, "{\"level\":1}");
        http.get("/v1/world/block?world=world&x=0&y=63&z=0");
        http.post("/v1/command", "{\"command\":\"list\"}");
        http.get("/v1/snapshot");
        assertThat(mock.callThreads).isNotEmpty().allMatch(MockView.THREAD::equals);
    }

    @Test
    void aStalledServerAnswersTimeoutNeverAPartialResult() throws IOException {
        mock.stalled = true;
        AgentClient.Reply reply = http.post("/v1/command", "{\"command\":\"list\",\"timeoutMs\":200}");
        assertThat(reply.status).isEqualTo(504);
        assertThat(reply.errorCode()).isEqualTo("TIMEOUT");
    }

    @Test
    void ticksMatchTheGoldens() throws IOException {
        replay("ticks/read");
        // The server's tick loop: the end-of-tick callback calls tick() once per tick.
        ScheduledExecutorService loop = Executors.newSingleThreadScheduledExecutor();
        try {
            loop.scheduleAtFixedRate(ticks::tick, 0, 50, TimeUnit.MILLISECONDS);
            replay("ticks/await");
        } finally {
            loop.shutdownNow();
        }
        assertThat(ticks.current()).isPositive();
    }

    @Test
    void eventsMatchTheGoldensOnTheRealBusWithGettersAsFields() throws IOException {
        // The goldens name Bukkit's PlayerJoinEvent; here the class is a NeoForge event on the real bus, and the fields
        // are its getters by method name, as the Bukkit agent records them (golden events/poll keeps the "get" prefix).
        String name = SampleEvents.Joined.class.getName();
        JsonNode subscribe = read("events/subscribe.request.json");
        ((ObjectNode) subscribe.get("body")).put("class", name);
        replay("events/subscribe", subscribe, read("events/subscribe.response.json"));
        CompletableFuture.runAsync(() -> bus.post(new SampleEvents.Joined("Alex", 3)), mock.serverThread).join();
        JsonNode poll = read("events/poll.response.json");
        ObjectNode event = (ObjectNode) poll.get("body").get("data").get("events").get(0);
        event.put("class", name);
        ObjectNode fields = event.putObject("fields");
        fields.put("getCount", 3);
        fields.put("getName", "Alex");
        replay("events/poll", read("events/poll.request.json"), poll);
        assertThat(http.get("/v1/events/probe-1?since=0").json().get("data").get("events").get(0).get("thread")
                .asText()).isEqualTo(MockView.THREAD);
        assertThat(http.send("DELETE", "/v1/events/probe-1", "Bearer " + AgentClient.TOKEN, null).status)
                .isEqualTo(200);
        assertThat(http.get("/v1/events/probe-1?since=0").errorCode()).isEqualTo("PROBE_NOT_FOUND");
    }

    @Test
    void logMatchesTheGoldenWithAStackTrace() throws IOException {
        Logger.getLogger("proofstone.contract").log(Level.WARNING, "a mod went away", new IllegalStateException("boom"));
        replay("log/read");
    }

    @Test
    void pluginEndpointsAreNotServedByANeoForgeAgent() throws IOException {
        for (String path : List.of("/v1/plugins/x/config?path=a", "/v1/proxy/servers")) {
            AgentClient.Reply reply = http.get(path);
            assertThat(reply.status).as(path).isEqualTo(404);
            assertThat(reply.errorCode()).as(path).isEqualTo("NOT_FOUND");
        }
        AgentClient.Reply reload = http.post("/v1/plugins/x/reload", null);
        assertThat(reload.status).isEqualTo(404);
    }

    private void replay(String name) throws IOException {
        replay(name, read(name + ".response.json"));
    }

    private void replay(String name, JsonNode expected) throws IOException {
        replay(name, read(name + ".request.json"), expected);
    }

    private void replay(String name, JsonNode request, JsonNode expected) throws IOException {
        String authorization = request.has("auth") && !request.get("auth").asBoolean()
                ? null : "Bearer " + AgentClient.TOKEN;
        String body = request.has("body") ? request.get("body").toString() : null;
        AgentClient.Reply actual = http.send(request.get("method").asText(), request.get("path").asText(),
                authorization, body);
        assertThat(actual.status).as(name + " status; body " + actual.body).isEqualTo(expected.get("status").asInt());
        assertThat(GoldenMatcher.mismatches(expected.get("body").toString(), actual.body))
                .as(name + " against the golden").isEmpty();
    }

    private static JsonNode read(String relative) throws IOException {
        return AgentClient.JSON.readTree(new File(GOLDEN, relative));
    }
}
