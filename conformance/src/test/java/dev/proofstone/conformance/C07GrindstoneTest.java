package dev.proofstone.conformance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.proofstone.api.Agent;
import dev.proofstone.api.Cell;
import dev.proofstone.api.CellTest;
import dev.proofstone.api.Click;
import dev.proofstone.api.Client;
import dev.proofstone.api.DualAssert;
import dev.proofstone.api.Item;
import dev.proofstone.api.ItemView;
import dev.proofstone.api.Matrix;
import dev.proofstone.api.PlayerHandle;
import dev.proofstone.api.Platform;
import dev.proofstone.api.Proofstone;
import dev.proofstone.api.Window;
import java.util.Map;

/**
 * C07 (specs/self-tests.md §1): a vanilla grindstone removes the enchantments from a sword. The input shows the
 * enchant, the result slot holds the bare sword, and taking it empties the input, on both oracles.
 *
 * <p>The grindstone arrived in 1.14. On 1.12.2 there is no such block, so the scenario is skipped there with that
 * reason. A skip is not a pass (docs/platforms/paper.md).
 */
@Proofstone
@Matrix(platform = Platform.PAPER, versions = {"latest"})
@Matrix(platform = Platform.FOLIA, versions = {"latest"})
@Matrix(platform = Platform.PURPUR, versions = {"latest"})
class C07GrindstoneTest {

    private static final Map<String, Integer> SHARPNESS_FIVE = Map.of("minecraft:sharpness", 5);

    @CellTest
    void grindstoneRemovesTheEnchantsOnBothOracles(Cell cell, Agent agent, Client client) {
        assumeTrue(hasGrindstone(cell.version()), "grindstone arrived in 1.14; " + cell.version()
                + " has no grindstone, so C07 cannot run there (docs/platforms/paper.md)");
        PlayerHandle player = ConformanceFixture.stand(agent, client);
        ConformanceFixture.placeBlock(agent, ConformanceFixture.GRINDSTONE, "grindstone");
        player.give(Item.of("diamond_sword").enchant("sharpness", 5));
        DualAssert.item(player, client).inInventory(ConformanceFixture.sword()).amount(1).enchants(SHARPNESS_FIVE);

        try (Window grindstone = client.openBlock(ConformanceFixture.GRINDSTONE)) {
            assertThat(grindstone.type()).isEqualTo("minecraft:grindstone");
            grindstone.moveFromInventory(ConformanceFixture.sword(), 0);
            grindstone.awaitSlot(0, item -> item.is("diamond_sword") && SHARPNESS_FIVE.equals(item.enchants()));

            ItemView result = grindstone.awaitSlot(2, item -> item.is("diamond_sword") && item.enchants().isEmpty());
            assertThat(result.enchants()).as("the result slot after grinding").isEmpty();

            grindstone.click(2, Click.LEFT);
            assertThat(grindstone.cursor().is("diamond_sword")).as("the bare sword on the cursor").isTrue();
            assertThat(grindstone.cursor().enchants()).as("cursor enchants").isEmpty();
            grindstone.awaitSlot(0, ItemView::isEmpty);
            ConformanceFixture.placeCursorInInventory(grindstone);
        }

        DualAssert.item(player, client).inInventory(ConformanceFixture.sword()).amount(1).enchants(Map.of());
    }

    /** Grindstones exist from 1.14 on. Versions are read as {@code major.minor.patch}; 26.x is newer than 1.14. */
    static boolean hasGrindstone(String version) {
        String[] parts = version.split("\\.");
        int major = Integer.parseInt(parts[0]);
        if (major > 1) {
            return true;
        }
        return parts.length > 1 && Integer.parseInt(parts[1]) >= 14;
    }
}
