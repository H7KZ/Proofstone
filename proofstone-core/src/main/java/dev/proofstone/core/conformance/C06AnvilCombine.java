package dev.proofstone.core.conformance;

import dev.proofstone.api.Agent;
import dev.proofstone.api.Cell;
import dev.proofstone.api.Click;
import dev.proofstone.api.Client;
import dev.proofstone.api.DualAssert;
import dev.proofstone.api.Item;
import dev.proofstone.api.ItemView;
import dev.proofstone.api.PlayerHandle;
import dev.proofstone.api.Window;
import java.util.Map;
import org.junit.jupiter.api.Assertions;

/**
 * C06 (specs/self-tests.md §1): a vanilla anvil combines a sword with an enchanted book. The result slot holds the
 * sword with the book's enchantment, the book is consumed, and the levels paid for the combine show on both oracles.
 * The cost is not hard-coded: it is whatever the server took, and both oracles must agree on it.
 */
public final class C06AnvilCombine {

    private static final int START_LEVEL = 30;
    private static final Map<String, Integer> SHARPNESS_FIVE = Map.of("minecraft:sharpness", 5);

    private C06AnvilCombine() {
    }

    /** Runs the scenario on a joined client. Throws an {@link AssertionError} when an oracle disagrees. */
    public static void run(Cell cell, Agent agent, Client client) {
        PlayerHandle player = ConformanceFixture.stand(agent, client);
        player.give(Item.of("diamond_sword")).give(Item.of("enchanted_book").storedEnchant("sharpness", 5))
                .level(START_LEVEL);

        try (Window anvil = client.openBlock(ConformanceFixture.ANVIL)) {
            anvil.moveFromInventory(ConformanceFixture.sword(), 0);
            anvil.moveFromInventory(item -> item.is("enchanted_book"), 1);
            ItemView result = anvil.awaitSlot(2,
                    item -> item.is("diamond_sword") && item.enchants().equals(SHARPNESS_FIVE));
            Assertions.assertTrue(result.storedEnchants().isEmpty(), "stored enchants of the result slot");

            anvil.click(2, Click.LEFT);
            ConformanceFixture.placeCursorInInventory(anvil);
        }

        DualAssert.item(player, client).inInventory(ConformanceFixture.sword()).amount(1).enchants(SHARPNESS_FIVE);
        int paid = START_LEVEL - player.awaitState("levels taken by the combine",
                state -> state.level() < START_LEVEL).level();
        Assertions.assertTrue(paid > 0, "levels the combine took");
        DualAssert.level(player, client).isEqualTo(START_LEVEL - paid);
        player.awaitState("book consumed",
                state -> state.inventory().values().stream().noneMatch(item -> item.is("enchanted_book")));
        Assertions.assertTrue(client.inventory().stream().noneMatch(item -> item.is("enchanted_book")),
                "client inventory after the combine");
    }
}
