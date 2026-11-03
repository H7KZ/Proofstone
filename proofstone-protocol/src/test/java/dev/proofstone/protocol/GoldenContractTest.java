package dev.proofstone.protocol;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.proofstone.protocol.testing.GoldenMatcher;
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

    private static final Map<String, Schemas> SCHEMAS = Map.of(
            "health/ok", new Schemas(null, "health.json"),
            "health/unauthorised", new Schemas(null, null),
            "players/not-found", new Schemas(null, null),
            "players/empty-inventory", new Schemas(null, "player-state.json"),
            "players/give-enchanted-sword", new Schemas("item.json", null),
            "players/read-after-give", new Schemas(null, "player-state.json"),
            "command/reply", new Schemas("command.json", "command.json#/$defs/reply"));

    /** The card's cases. The list is fixed so that a deleted golden file fails the build. */
    private static final List<String> CASES = List.of(
            "command/reply",
            "health/ok",
            "health/unauthorised",
            "players/empty-inventory",
            "players/give-enchanted-sword",
            "players/not-found",
            "players/read-after-give");

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

    @Test
    void giveThenReadBackKeepsTheEnchantLevel() throws IOException {
        JsonNode response = JSON.readTree(read("players/read-after-give", "response"));

        PlayerState state = JSON.treeToValue(response.at("/body/data"), PlayerState.class);

        assertThat(state.inventory().get("0").enchants()).containsEntry("minecraft:sharpness", 32767);
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
