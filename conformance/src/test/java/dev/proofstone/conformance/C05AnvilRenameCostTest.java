package dev.proofstone.conformance;

import static org.assertj.core.api.Assertions.assertThat;

import dev.proofstone.api.Agent;
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

/**
 * C05 (specs/self-tests.md §1): a vanilla anvil rename. The result slot shows the new name, taking it costs one
 * experience level (vanilla charges one level for a rename with no other work), and the experience sync reaches both
 * oracles.
 */
@Proofstone
@Matrix(platform = Platform.PAPER, versions = {"latest"})
@Matrix(platform = Platform.FOLIA, versions = {"latest"})
class C05AnvilRenameCostTest {

    private static final int START_LEVEL = 10;
    private static final int RENAME_COST = 1;

    @CellTest
    void renamingASwordCostsOneLevelOnBothOracles(Agent agent, Client client) {
        PlayerHandle player = ConformanceFixture.stand(agent, client);
        player.give(Item.of("diamond_sword")).level(START_LEVEL);
        DualAssert.level(player, client).isEqualTo(START_LEVEL);

        try (Window anvil = client.openBlock(ConformanceFixture.ANVIL)) {
            assertThat(anvil.type()).isEqualTo("minecraft:anvil");
            anvil.moveFromInventory(ConformanceFixture.sword(), 0);
            anvil.awaitSlot(0, ConformanceFixture.sword());

            anvil.rename("Blade");
            ItemView result = anvil.awaitSlot(2, item -> item.is("diamond_sword") && "Blade".equals(item.name()));
            assertThat(result.name()).isEqualTo("Blade");

            anvil.click(2, Click.LEFT);
            assertThat(anvil.cursor().name()).as("the renamed sword on the cursor").isEqualTo("Blade");
            ConformanceFixture.placeCursorInInventory(anvil);
        }

        DualAssert.item(player, client).inInventory(ConformanceFixture.sword()).amount(1).name("Blade");
        DualAssert.level(player, client).isEqualTo(START_LEVEL - RENAME_COST);
    }
}
