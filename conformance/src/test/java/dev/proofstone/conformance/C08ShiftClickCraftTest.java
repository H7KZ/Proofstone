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
import java.util.function.Predicate;

/**
 * C08 (specs/self-tests.md §1): a shift-click on a crafting table's result moves the whole result into the player's
 * inventory in one click. Three sugar cane in the bottom row make three paper; the ingredients are used up and the
 * result is gone, on both oracles.
 */
@Proofstone
@Matrix(platform = Platform.PAPER, versions = {"latest"})
@Matrix(platform = Platform.FOLIA, versions = {"latest"})
@Matrix(platform = Platform.PURPUR, versions = {"latest"})
class C08ShiftClickCraftTest {

    private static final int BOTTOM_ROW_FIRST_SLOT = 7;
    private static final int GRID_LAST_SLOT = 9;

    @CellTest
    void shiftClickMovesTheCraftingResultIntoTheInventory(Agent agent, Client client) {
        PlayerHandle player = ConformanceFixture.stand(agent, client);
        ConformanceFixture.placeBlock(agent, ConformanceFixture.CRAFTING_TABLE, "crafting_table");
        player.give(Item.of("sugar_cane").amount(3));
        DualAssert.item(player, client).inInventory(sugarCane()).amount(3);

        try (Window table = client.openBlock(ConformanceFixture.CRAFTING_TABLE)) {
            assertThat(table.type()).isEqualTo("minecraft:crafting");
            // Pick the stack up, then right-click the bottom row: one cane per slot.
            table.click(ConformanceFixture.playerSlot(table, sugarCane()), Click.LEFT);
            for (int slot = BOTTOM_ROW_FIRST_SLOT; slot <= GRID_LAST_SLOT; slot++) {
                table.click(slot, Click.RIGHT);
            }
            assertThat(table.cursor().isEmpty()).as("cursor after placing the three canes").isTrue();

            ItemView result = table.awaitSlot(0, item -> item.is("paper") && item.amount() == 3);
            assertThat(result.amount()).isEqualTo(3);

            table.click(0, Click.SHIFT_LEFT);
            table.awaitSlot(0, ItemView::isEmpty);
            assertThat(table.cursor().isEmpty()).as("cursor after shift-click").isTrue();
            assertThat(table.slots().subList(1, GRID_LAST_SLOT + 1)).as("crafting grid after the craft")
                    .allMatch(ItemView::isEmpty);
        }

        DualAssert.item(player, client).inInventory(item -> item.is("paper")).amount(3);
        player.awaitState("sugar cane used up",
                state -> state.inventory().values().stream().noneMatch(item -> item.is("sugar_cane")));
        assertThat(client.inventory()).as("client inventory after the craft")
                .noneMatch(item -> item.is("sugar_cane"));
    }

    private static Predicate<ItemView> sugarCane() {
        return item -> item.is("sugar_cane");
    }
}
