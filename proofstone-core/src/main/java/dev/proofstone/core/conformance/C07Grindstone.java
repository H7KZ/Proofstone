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
 * C07 (specs/self-tests.md §1): a vanilla grindstone removes the enchantments from a sword. The input shows the
 * enchant, the result slot holds the bare sword, and taking it empties the input, on both oracles.
 *
 * <p>The grindstone arrived in 1.14. The conformance wrapper skips the scenario on older rows with that reason
 * ({@link #hasGrindstone}); this class does not skip (docs/platforms/paper.md).
 */
public final class C07Grindstone {

    private static final Map<String, Integer> SHARPNESS_FIVE = Map.of("minecraft:sharpness", 5);

    private C07Grindstone() {
    }

    /** Runs the scenario on a joined client. Throws an {@link AssertionError} when an oracle disagrees. */
    public static void run(Cell cell, Agent agent, Client client) {
        PlayerHandle player = ConformanceFixture.stand(agent, client);
        ConformanceFixture.placeBlock(agent, ConformanceFixture.GRINDSTONE, "grindstone");
        player.give(Item.of("diamond_sword").enchant("sharpness", 5));
        DualAssert.item(player, client).inInventory(ConformanceFixture.sword()).amount(1).enchants(SHARPNESS_FIVE);

        try (Window grindstone = client.openBlock(ConformanceFixture.GRINDSTONE)) {
            Assertions.assertEquals("minecraft:grindstone", grindstone.type(), "grindstone window type");
            grindstone.moveFromInventory(ConformanceFixture.sword(), 0);
            grindstone.awaitSlot(0, item -> item.is("diamond_sword") && SHARPNESS_FIVE.equals(item.enchants()));

            ItemView result = grindstone.awaitSlot(2, item -> item.is("diamond_sword") && item.enchants().isEmpty());
            Assertions.assertTrue(result.enchants().isEmpty(), "the result slot after grinding");

            grindstone.click(2, Click.LEFT);
            Assertions.assertTrue(grindstone.cursor().is("diamond_sword"), "the bare sword on the cursor");
            Assertions.assertTrue(grindstone.cursor().enchants().isEmpty(), "cursor enchants");
            grindstone.awaitSlot(0, ItemView::isEmpty);
            ConformanceFixture.placeCursorInInventory(grindstone);
        }

        DualAssert.item(player, client).inInventory(ConformanceFixture.sword()).amount(1).enchants(Map.of());
    }

    /** Grindstones exist from 1.14 on. Versions are read as {@code major.minor.patch}; 26.x is newer than 1.14. */
    public static boolean hasGrindstone(String version) {
        String[] parts = version.split("\\.");
        int major = Integer.parseInt(parts[0]);
        if (major > 1) {
            return true;
        }
        return parts.length > 1 && Integer.parseInt(parts[1]) >= 14;
    }
}
