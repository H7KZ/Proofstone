package dev.proofstone.examples.disenchantment;

import static org.assertj.core.api.Assertions.assertThat;

import dev.proofstone.api.Agent;
import dev.proofstone.api.BlockPos;
import dev.proofstone.api.CapturedEvent;
import dev.proofstone.api.CellTest;
import dev.proofstone.api.Click;
import dev.proofstone.api.Client;
import dev.proofstone.api.DualAssert;
import dev.proofstone.api.EventProbe;
import dev.proofstone.api.Item;
import dev.proofstone.api.ItemView;
import dev.proofstone.api.Matrix;
import dev.proofstone.api.PlayerHandle;
import dev.proofstone.api.Platform;
import dev.proofstone.api.Position;
import dev.proofstone.api.Proofstone;
import dev.proofstone.api.Window;
import java.util.List;
import java.util.Map;

/**
 * The Disenchantment anvil scenario (survey section 6, T-M2-10) against the real release jar that
 * {@code downloadDisenchantment} pins by SHA-256: a sword with Sharpness V and Unbreaking III and a plain book go into
 * an anvil, the plugin offers an enchanted book, and taking it moves the enchantments and charges experience.
 *
 * <p>Both oracles check the outcome. The plugin's own {@code PostDisenchantEvent} gives the cost, so the level check
 * is {@code 30 - xpCost} and never a number copied from a run. A logged error fails the test through the lifecycle's
 * error-log check (test-api section 3), after every test.
 */
@Proofstone(plugins = "build/disenchantment/Disenchantment-6.5.12.jar")
@Matrix(platform = Platform.PAPER, versions = {"latest"})
class AnvilDisenchantE2E {

    private static final String POST_DISENCHANT = "com.jankominek.disenchantment.events.api.PostDisenchantEvent";
    private static final BlockPos ANVIL = new BlockPos(0, 64, 2);
    private static final Map<String, Integer> ENCHANTS = Map.of("minecraft:sharpness", 5, "minecraft:unbreaking", 3);
    private static final int START_LEVEL = 30;

    @CellTest
    void disenchantMovesEnchantsToTheBookAndChargesTheEventCost(Agent agent, Client client) {
        // No enchantment is left to chance, so every enchantment transfers. The default is already empty; patching it
        // states the dependency, and the framework restores the config after the test.
        agent.plugin("Disenchantment").patchConfig("disenchantment.enchantment-chances", Map.of());

        PlayerHandle player = standNextToAnAnvil(agent, client);
        player.level(START_LEVEL)
                .give(Item.of("diamond_sword").enchant("sharpness", 5).enchant("unbreaking", 3))
                .give(Item.of("book"));
        DualAssert.item(player, client).inInventory(item -> item.is("diamond_sword")).enchants(ENCHANTS);
        DualAssert.level(player, client).isEqualTo(START_LEVEL);

        try (EventProbe post = agent.events().subscribe(POST_DISENCHANT)) {
            try (Window anvil = client.openBlock(ANVIL)) {
                assertThat(anvil.type()).isEqualTo("minecraft:anvil");
                anvil.moveFromInventory(item -> item.is("diamond_sword"), 0);
                anvil.moveFromInventory(item -> item.is("book"), 1);

                // The plugin computes this result; the client sees it only if the server sent it.
                ItemView offered = anvil.awaitSlot(2, item -> item.is("enchanted_book"));
                assertThat(offered.storedEnchants()).isEqualTo(ENCHANTS);

                anvil.click(2, Click.LEFT);
                // The plugin leaves the stripped sword in the input slot and puts the book on the cursor itself.
                ItemView stripped = anvil.awaitSlot(0, item -> item.is("diamond_sword") && item.enchants().isEmpty());
                assertThat(stripped.amount()).isEqualTo(1);
                assertThat(anvil.cursor().is("enchanted_book")).isTrue();
                // Place the book in the first free player slot (window slots 0 to 2 are the anvil's own, and the
                // result slot would refuse it).
                anvil.click(firstFreePlayerSlot(anvil), Click.LEFT);
            }

            // The probe saw the plugin's own event, so the cost is the plugin's number and not one copied from a run.
            int xpCost = post.awaitOne().getInt("xpCost");
            assertThat(xpCost).as("PostDisenchantEvent xpCost").isPositive().isLessThan(START_LEVEL);

            // After the close the client resyncs its inventory. Both oracles must agree on every line below.
            DualAssert.item(player, client).inInventory(item -> item.is("enchanted_book")).amount(1)
                    .storedEnchants(ENCHANTS).enchants(Map.of());
            DualAssert.item(player, client).inInventory(item -> item.is("diamond_sword")).amount(1)
                    .enchants(Map.of()).storedEnchants(Map.of());
            DualAssert.level(player, client).isEqualTo(START_LEVEL - xpCost);
            assertThat(post.events()).as("one operation fires one PostDisenchantEvent").hasSize(1);
        }
    }

    /** The first empty slot after the anvil's own three, that is the player's inventory in server order. */
    private static int firstFreePlayerSlot(Window window) {
        List<ItemView> slots = window.slots();
        for (int slot = 3; slot < slots.size(); slot++) {
            if (slots.get(slot).isEmpty()) {
                return slot;
            }
        }
        throw new IllegalStateException("no free player slot in the anvil window: " + slots);
    }

    /** Joins the world at a spot with ground, an anvil in reach and an empty inventory. */
    private static PlayerHandle standNextToAnAnvil(Agent agent, Client client) {
        // Reset first: it also teleports the player to the fixture spot, which would undo the teleport below.
        PlayerHandle player = agent.player(client).reset();
        player.awaitState("empty inventory", state -> state.inventory().isEmpty());
        // Chunks load around players only, and flat worlds spawn underground (docs/platforms/paper.md).
        agent.command("setblock 0 63 0 minecraft:stone");
        agent.command("setblock 0 63 2 minecraft:stone");
        agent.command("setblock 0 64 2 minecraft:anvil");
        agent.command("tp " + client.name() + " 0.5 64 0.5");
        Position at = client.awaitPosition("teleport to the anvil", p -> p.y() == 64.0);
        assertThat(at.x()).isEqualTo(0.5);
        return player;
    }
}
