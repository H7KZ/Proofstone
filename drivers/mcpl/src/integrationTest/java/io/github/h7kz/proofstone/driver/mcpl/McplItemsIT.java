package io.github.h7kz.proofstone.driver.mcpl;

import io.github.h7kz.proofstone.api.ItemView;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.h7kz.proofstone.core.await.Await;
import io.github.h7kz.proofstone.core.cell.Cell;
import io.github.h7kz.proofstone.core.cell.CellPlanner;
import io.github.h7kz.proofstone.api.BlockPos;
import io.github.h7kz.proofstone.core.driver.ClientSession;
import io.github.h7kz.proofstone.core.driver.ConnectSpec;
import io.github.h7kz.proofstone.core.driver.Face;
import io.github.h7kz.proofstone.core.driver.ItemViews;
import io.github.h7kz.proofstone.core.driver.WindowSession;
import io.github.h7kz.proofstone.core.host.Eula;
import io.github.h7kz.proofstone.core.host.PaperHost;
import io.github.h7kz.proofstone.core.host.PaperHostOptions;
import io.github.h7kz.proofstone.core.versions.VersionsCatalog;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * T-M2-06 on a real Paper server: items given through the agent are parsed from the data components the client
 * receives, and equal what the agent reports for the same slot. Enchant ids are resolved from the registry the
 * server sent in the configuration phase, item ids from the bundled list. Needs Docker and an accepted EULA
 * ({@value Eula#VARIABLE}=true); without it the class is skipped with that reason (ADR-0005).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class McplItemsIT {

    private static final Duration WAIT = Duration.ofSeconds(20);
    private static final String PLAYER = "ItemBot";
    private static final ObjectMapper JSON = new ObjectMapper();

    private PaperHost host;
    private ClientSession bot;
    private final HttpClient http = HttpClient.newHttpClient();

    @BeforeAll
    void bootAndJoin() {
        assumeTrue(Eula.accepted(), "Minecraft EULA not accepted: set " + Eula.VARIABLE + "=true");
        Path root = Path.of(System.getProperty("proofstone.repoRoot"));
        VersionsCatalog catalog = VersionsCatalog.load(root.resolve("versions.yml"),
                root.resolve("proofstone-protocol/schemas/versions.schema.json"));
        Cell cell = new CellPlanner(catalog).plan("paper", List.of("latest")).get(0);
        // The agent port is published (debug mode) so this test can call the agent's player endpoints directly.
        host = PaperHost.start(cell, PaperHostOptions.of(Path.of(System.getProperty("proofstone.agentJar")), true)
                .withDebugPorts(true));
        bot = new McplDriver().connect(new ConnectSpec(host.gameHost(), host.gamePort(), PLAYER,
                Duration.ofSeconds(60)));
        bot.awaitMessage(Pattern.compile("joined"), WAIT);
        // Chunks load around players only, and flat worlds spawn underground (docs/platforms/paper.md).
        host.command("setblock 0 63 0 minecraft:stone");
        host.command("setblock 0 63 2 minecraft:stone");
        host.command("setblock 0 64 2 minecraft:anvil");
        host.command("tp " + PLAYER + " 0.5 64 0.5");
        Await.until("teleport of " + PLAYER + " seen by the client", ((McplSession) bot)::position,
                p -> p != null && p[1] == 64.0, WAIT);
    }

    @AfterAll
    void stop() {
        if (bot != null) {
            bot.close();
        }
        if (host != null) {
            host.close();
        }
    }

    @Test
    void enchantedSwordAndBookParseToWhatTheAgentReports() throws Exception {
        // Level 255 is the most Paper 26.3 stores (docs/platforms/paper.md); the agent and the parser pass it through.
        give("{\"type\":\"minecraft:diamond_sword\",\"amount\":1,\"name\":\"Blade\",\"lore\":[\"first\",\"second\"],"
                + "\"damage\":12,\"enchants\":{\"minecraft:sharpness\":255,\"minecraft:unbreaking\":3}}");
        give("{\"type\":\"minecraft:enchanted_book\",\"amount\":1,\"storedEnchants\":"
                + "{\"minecraft:sharpness\":5,\"minecraft:mending\":1}}");

        ItemViews inventory = (ItemViews) bot.inventory();
        Await.until("sword and book parsed from window 0", inventory::itemViews,
                views -> views.stream().anyMatch(v -> v.type().equals("minecraft:diamond_sword"))
                        && views.stream().anyMatch(v -> v.type().equals("minecraft:enchanted_book")), WAIT);

        // Hotbar slot n is window 0 slot 36 + n; the agent numbers the inventory 0..35 (hotbar first).
        JsonNode agentInventory = Await.until("agent inventory of " + PLAYER, this::agentInventory,
                inv -> inv.has("0") && inv.has("1"), WAIT);
        System.out.println("agent inventory: " + agentInventory);

        ItemView sword = inventory.itemViews().get(36);
        ItemView book = inventory.itemViews().get(37);

        // Same slot, both oracles: type, amount, name and enchants must be equal.
        JsonNode agentSword = agentInventory.get("0");
        assertThat(sword.type()).isEqualTo(agentSword.get("type").asText());
        assertThat(sword.amount()).isEqualTo(agentSword.get("amount").asInt());
        assertThat(sword.name()).isEqualTo(agentSword.get("name").asText());
        assertThat(sword.enchants()).isEqualTo(levels(agentSword.get("enchants")))
                .containsOnly(Map.entry("minecraft:sharpness", 255), Map.entry("minecraft:unbreaking", 3));
        JsonNode agentBook = agentInventory.get("1");
        assertThat(book.type()).isEqualTo(agentBook.get("type").asText());
        assertThat(book.enchants()).isEqualTo(levels(agentBook.get("enchants"))).isEmpty();

        // The agent's item has no lore, damage or stored enchants field yet (T-M2-02 widens it), so these are checked
        // against what the agent was asked to build, and against the agent's raw serialisation as far as it shows them.
        assertThat(sword.lore()).containsExactly("first", "second");
        assertThat(sword.damage()).isEqualTo(12);
        assertThat(book.storedEnchants()).containsOnly(Map.entry("minecraft:sharpness", 5),
                Map.entry("minecraft:mending", 1));
        String swordRaw = agentSword.get("raw").asText();
        String bookRaw = agentBook.get("raw").asText();
        assertThat(swordRaw).contains("first").contains("second").contains("Blade");
        assertThat(bookRaw).contains("sharpness").contains("mending");

        // Through an open window: the player's 36 slots trail the container's own, and parse to the same views.
        bot.useBlock(new BlockPos(0, 64, 2), Face.UP);
        WindowSession anvil = bot.awaitWindow(WAIT);
        List<ItemView> anvilViews = ((ItemViews) anvil).itemViews();
        assertThat(anvilViews).hasSize(3 + 36);
        assertThat(anvilViews.subList(0, 3)).allMatch(ItemView::isEmpty);
        assertThat(anvilViews.get(3 + 27)).isEqualTo(sword);
        assertThat(anvilViews.get(3 + 28)).isEqualTo(book);
        assertThat(((ItemViews) anvil).cursorView().isEmpty()).isTrue();
        anvil.close();
    }

    private void give(String item) throws Exception {
        JsonNode reply = agent("POST", "/v1/players/" + PLAYER + "/give", item);
        assertThat(reply.get("ok").asBoolean()).as("give reply %s", reply).isTrue();
    }

    private JsonNode agentInventory() {
        try {
            return agent("GET", "/v1/players/" + PLAYER, null).get("data").get("inventory");
        } catch (Exception e) {
            throw new IllegalStateException("agent player read failed", e);
        }
    }

    private JsonNode agent(String method, String path, String body) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://" + host.gameHost() + ":"
                        + host.debugAgentPort() + path))
                .header("Authorization", "Bearer " + host.agentToken())
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(10));
        request.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body));
        HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as("%s %s -> %s", method, path, response.body()).isEqualTo(200);
        return JSON.readTree(response.body());
    }

    private static Map<String, Integer> levels(JsonNode node) {
        Map<String, Integer> out = new LinkedHashMap<>();
        node.fields().forEachRemaining(e -> out.put(e.getKey(), e.getValue().asInt()));
        return out;
    }
}
