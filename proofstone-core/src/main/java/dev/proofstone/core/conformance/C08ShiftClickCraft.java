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
import java.util.function.Predicate;
import org.junit.jupiter.api.Assertions;

/**
 * C08 (specs/self-tests.md §1): a shift-click on a crafting table's result moves the whole result into the player's
 * inventory in one click. Three sugar cane in the bottom row make three paper; the ingredients are used up and the
 * result is gone, on both oracles.
 */
public final class C08ShiftClickCraft {

    private static final int BOTTOM_ROW_FIRST_SLOT = 7;
    private static final int GRID_LAST_SLOT = 9;

    private C08ShiftClickCraft() {
    }

    /** Runs the scenario on a joined client. Throws an {@link AssertionError} when an oracle disagrees. */
    public static void run(Cell cell, Agent agent, Client client) {
        PlayerHandle player = ConformanceFixture.stand(agent, client);
        ConformanceFixture.placeBlock(agent, ConformanceFixture.CRAFTING_TABLE, "crafting_table");
        player.give(Item.of("sugar_cane").amount(3));
        DualAssert.item(player, client).inInventory(sugarCane()).amount(3);

        try (Window table = client.openBlock(ConformanceFixture.CRAFTING_TABLE)) {
            Assertions.assertEquals("minecraft:crafting", table.type(), "crafting window type");
            // Pick the stack up, then right-click the bottom row: one cane per slot.
            table.click(ConformanceFixture.playerSlot(table, sugarCane()), Click.LEFT);
            for (int slot = BOTTOM_ROW_FIRST_SLOT; slot <= GRID_LAST_SLOT; slot++) {
                table.click(slot, Click.RIGHT);
            }
            Assertions.assertTrue(table.cursor().isEmpty(), "cursor after placing the three canes");

            ItemView result = table.awaitSlot(0, item -> item.is("paper") && item.amount() == 3);
            Assertions.assertEquals(3, result.amount(), "amount of the crafted result");

            table.click(0, Click.SHIFT_LEFT);
            table.awaitSlot(0, ItemView::isEmpty);
            Assertions.assertTrue(table.cursor().isEmpty(), "cursor after shift-click");
            Assertions.assertTrue(table.slots().subList(1, GRID_LAST_SLOT + 1).stream().allMatch(ItemView::isEmpty),
                    "crafting grid after the craft");
        }

        DualAssert.item(player, client).inInventory(item -> item.is("paper")).amount(3);
        player.awaitState("sugar cane used up",
                state -> state.inventory().values().stream().noneMatch(item -> item.is("sugar_cane")));
        Assertions.assertTrue(client.inventory().stream().noneMatch(item -> item.is("sugar_cane")),
                "client inventory after the craft");
    }

    private static Predicate<ItemView> sugarCane() {
        return item -> item.is("sugar_cane");
    }
}
