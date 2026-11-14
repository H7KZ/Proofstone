package io.github.h7kz.proofstone.protocol;

import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.h7kz.proofstone.protocol.testing.GoldenMatcher;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/** Validates every golden case in {@code golden/} against the v1 schemas (golden/README.md). */
class GoldenContractTest {

    private static final Path GOLDEN = Path.of("golden");
    private static final ObjectMapper JSON = new ObjectMapper();

    /** Schemas for a case's request body and for its {@code data}. A null means there is none. */
    private record Schemas(String request, String data) {
    }

    private static final Map<String, Schemas> SCHEMAS = Map.ofEntries(
            entry("health/ok", new Schemas(null, "health.json")),
            entry("health/proxy-ok", new Schemas(null, "health.json")),
            entry("health/unauthorised", new Schemas(null, null)),
            entry("datapacks/list", new Schemas(null, "datapacks.json")),
            entry("mods/list", new Schemas(null, "mods.json")),
            entry("proxy/servers", new Schemas(null, "proxy.json#/$defs/servers")),
            entry("proxy/server-get", new Schemas(null, "proxy.json")),
            entry("proxy/server-not-found", new Schemas(null, null)),
            entry("players/proxy-first-join", new Schemas(null, "proxy.json#/$defs/player")),
            entry("players/proxy-switched", new Schemas(null, "proxy.json#/$defs/player")),
            entry("players/not-found", new Schemas(null, null)),
            entry("players/empty-inventory", new Schemas(null, "player-state.json")),
            entry("players/give-enchanted-sword", new Schemas("item.json", null)),
            entry("players/read-after-give", new Schemas(null, "player-state.json")),
            entry("players/open-anvil", new Schemas(null, "player-state.json")),
            entry("command/reply", new Schemas("command.json", "command.json#/$defs/reply")),
            entry("world/block-get", new Schemas(null, "block.json")),
            entry("world/block-put", new Schemas("block.json#/$defs/write", "block.json")),
            entry("plugins/config-patch", new Schemas("config.json#/$defs/patch", "config.json#/$defs/empty")),
            entry("plugins/config-read", new Schemas(null, "config.json#/$defs/read")),
            entry("plugins/config-restore", new Schemas(null, "config.json#/$defs/empty")),
            entry("plugins/config-read-after-restore", new Schemas(null, "config.json#/$defs/read")),
            entry("plugins/reload", new Schemas(null, "config.json#/$defs/empty")),
            entry("plugins/reload-hook", new Schemas("reload.json", "config.json#/$defs/empty")),
            entry("ticks/read", new Schemas(null, "ticks.json")),
            entry("ticks/await", new Schemas("ticks.json#/$defs/await", "ticks.json")),
            entry("events/subscribe", new Schemas("event.json#/$defs/subscribe", "event.json#/$defs/subscribeReply")),
            entry("events/poll", new Schemas(null, "event.json#/$defs/page")),
            entry("log/read", new Schemas(null, "log-record.json#/$defs/page")),
            entry("snapshot/read", new Schemas(null, "snapshot.json")));

    /** The card's cases. The list is fixed so that a deleted golden file fails the build. */
    private static final List<String> CASES = List.of(
            "command/reply",
            "datapacks/list",
            "events/poll",
            "events/subscribe",
            "health/ok",
            "health/proxy-ok",
            "health/unauthorised",
            "log/read",
            "mods/list",
            "players/empty-inventory",
            "players/give-enchanted-sword",
            "players/not-found",
            "players/open-anvil",
            "players/proxy-first-join",
            "players/proxy-switched",
            "players/read-after-give",
            "proxy/server-get",
            "proxy/server-not-found",
            "proxy/servers",
            "snapshot/read",
            "plugins/config-patch",
            "plugins/config-read",
            "plugins/config-read-after-restore",
            "plugins/config-restore",
            "plugins/reload",
            "plugins/reload-hook",
            "ticks/await",
            "ticks/read",
            "world/block-get",
            "world/block-put");

    static List<String> cases() {
        return CASES;
    }

    @Test
    void casesMatchTheCard() throws IOException {
        TreeSet<String> found = new TreeSet<>();
        try (Stream<Path> files = Files.walk(GOLDEN)) {
            files.filter(f -> f.toString().endsWith(".request.json"))
                    .forEach(f -> found.add(caseId(f)));
        }

        assertThat(found).containsExactlyElementsOf(new TreeSet<>(CASES));
        assertThat(SCHEMAS.keySet()).containsExactlyInAnyOrderElementsOf(CASES);
    }

    @ParameterizedTest
    @MethodSource("cases")
    void requestAndResponseMatchTheSchemas(String caseId) throws IOException {
        JsonNode request = JSON.readTree(read(caseId, "request"));
        JsonNode response = JSON.readTree(read(caseId, "response"));
        Schemas schemas = SCHEMAS.get(caseId);

        if (schemas.request() != null) {
            assertThat(ProtocolSchemas.errors(schemas.request(), concrete(request.get("body"))))
                    .as("request body").isEmpty();
        }
        assertThat(ProtocolSchemas.errors("envelope.json", concrete(response.get("body"))))
                .as("response envelope").isEmpty();
        if (schemas.data() != null) {
            assertThat(ProtocolSchemas.errors(schemas.data(), concrete(response.at("/body/data"))))
                    .as("response data").isEmpty();
        }
    }

    /** Protocol 1.3 (ADR-0017 section 9): the one existing golden that moves is health/ok; proxy goldens stay at 1.2. */
    @Test
    void onlyHealthOkMovesToProtocolOneThree() throws IOException {
        assertThat(JSON.readTree(read("health/ok", "response")).at("/body/data/protocol").asText()).isEqualTo("1.3");
        assertThat(JSON.readTree(read("health/proxy-ok", "response")).at("/body/data/protocol").asText())
                .isEqualTo("1.2");
    }

    /** The new reads are plain GETs with no body, served only where the agent reports the capability token. */
    @Test
    void protocolOneThreeReadsAreBodylessGets() throws IOException {
        JsonNode datapacks = JSON.readTree(read("datapacks/list", "request"));
        JsonNode mods = JSON.readTree(read("mods/list", "request"));

        assertThat(datapacks.get("method").asText()).isEqualTo("GET");
        assertThat(datapacks.get("path").asText()).isEqualTo("/v1/datapacks");
        assertThat(datapacks.has("body")).isFalse();
        assertThat(mods.get("method").asText()).isEqualTo("GET");
        assertThat(mods.get("path").asText()).isEqualTo("/v1/mods");
        assertThat(mods.has("body")).isFalse();
    }

    /** Mods are sorted by id (agent-protocol.md section 2), so the golden is too. */
    @Test
    void theModsGoldenIsSortedById() throws IOException {
        List<String> ids = new java.util.ArrayList<>();
        JSON.readTree(read("mods/list", "response")).at("/body/data/mods").forEach(m -> ids.add(m.get("id").asText()));

        assertThat(ids).isSortedAccordingTo(String::compareTo).isNotEmpty();
    }

    @Test
    void giveThenReadBackKeepsTheEnchantLevel() throws IOException {
        JsonNode response = JSON.readTree(read("players/read-after-give", "response"));

        PlayerState state = JSON.treeToValue(response.at("/body/data"), PlayerState.class);

        assertThat(state.inventory().get("0").enchants()).containsEntry("minecraft:sharpness", 255);
    }

    private static String read(String caseId, String part) throws IOException {
        return Files.readString(GOLDEN.resolve(caseId + "." + part + ".json"));
    }

    private static String concrete(JsonNode node) {
        return GoldenMatcher.concrete(node.toString());
    }

    private static String caseId(Path request) {
        String relative = GOLDEN.relativize(request).toString().replace('\\', '/');
        return relative.substring(0, relative.length() - ".request.json".length());
    }
}
