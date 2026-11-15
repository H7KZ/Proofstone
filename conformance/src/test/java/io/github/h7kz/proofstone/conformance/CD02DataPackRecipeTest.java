package io.github.h7kz.proofstone.conformance;

import io.github.h7kz.proofstone.api.Agent;
import io.github.h7kz.proofstone.api.CellTest;
import io.github.h7kz.proofstone.api.Click;
import io.github.h7kz.proofstone.api.Client;
import io.github.h7kz.proofstone.api.DualAssert;
import io.github.h7kz.proofstone.api.Item;
import io.github.h7kz.proofstone.api.ItemView;
import io.github.h7kz.proofstone.api.Matrix;
import io.github.h7kz.proofstone.api.Platform;
import io.github.h7kz.proofstone.api.PlayerHandle;
import io.github.h7kz.proofstone.api.Proofstone;
import io.github.h7kz.proofstone.api.Window;
import io.github.h7kz.proofstone.core.conformance.ConformanceFixture;
import io.github.h7kz.proofstone.core.await.Await;
import java.time.Duration;
import org.junit.jupiter.api.Assertions;

/**
 * C-D02 (specs/self-tests.md section 1c): a data-pack recipe (two dirt make one diamond, which vanilla has no recipe for)
 * crafts through the client's crafting window, as C08 does. The result shows in the window's result slot (the client's
 * view of the server's recipe), and after the window is closed the agent and the client read the same inventory. The
 * Fabric and NeoForge agents cannot read a player while a window is open (501 NOT_SUPPORTED, docs/platforms/fabric.md), so
 * like C08 this reads the player only before the window opens and after it closes; nothing is weakened for them.
 */
@Proofstone(datapacks = "src/test/datapacks/proofstone-e2e")
@Matrix(platform = Platform.PAPER, versions = {"latest"})
// Loader rows are experimental, so they run only when -Dproofstone.cells names them (fabric@26.3), T-M8-08.
@Matrix(platform = Platform.FABRIC, versions = {"26.3"})
@Matrix(platform = Platform.NEOFORGE, versions = {"26.3"})
class CD02DataPackRecipeTest {

    private static final int FIRST_GRID_SLOT = 1;
    private static final int SECOND_GRID_SLOT = 2;
    private static final int GRID_LAST_SLOT = 9;

    @CellTest
    void aDataPackRecipeCraftsThroughTheClientsWindowOnBothOracles(Agent agent, Client client) {
        PlayerHandle player = ConformanceFixture.stand(agent, client);
        ConformanceFixture.placeBlock(agent, ConformanceFixture.CRAFTING_TABLE, "crafting_table");
        player.give(Item.of("dirt").amount(2));
        DualAssert.item(player, client).inInventory(item -> item.is("dirt")).amount(2);

        try (Window table = client.openBlock(ConformanceFixture.CRAFTING_TABLE)) {
            Assertions.assertEquals("minecraft:crafting", table.type(), "crafting window type");
            table.click(ConformanceFixture.playerSlot(table, item -> item.is("dirt")), Click.LEFT);
            table.click(FIRST_GRID_SLOT, Click.RIGHT);
            table.click(SECOND_GRID_SLOT, Click.RIGHT);
            Assertions.assertTrue(table.cursor().isEmpty(), "cursor after placing the two dirt");

            ItemView result = table.awaitSlot(0, item -> item.is("diamond") && item.amount() == 1);
            Assertions.assertEquals(1, result.amount(), "amount of the crafted result");

            table.click(0, Click.SHIFT_LEFT);
            table.awaitSlot(0, ItemView::isEmpty);
            // The server sends the result slot first and the player's own slot after it, and a close sent in between
            // drops that late update (the driver, like the vanilla client, ignores a closed window's packets). So the
            // diamond must be in the client's own view of the window before it is closed (T-M8-09 finding: Fabric runs
            // 2 and 3, the update for slot 45 arrived 1 ms after the close).
            Await.until("the diamond in the window's player slots", () -> table.slots().stream()
                    .anyMatch(item -> item.is("diamond")), found -> found, Duration.ofSeconds(10));
            Assertions.assertTrue(table.slots().subList(1, GRID_LAST_SLOT + 1).stream().allMatch(ItemView::isEmpty),
                    "crafting grid after the craft");
        }

        DualAssert.item(player, client).inInventory(item -> item.is("diamond")).amount(1);
        player.awaitState("dirt used up",
                state -> state.inventory().values().stream().noneMatch(item -> item.is("dirt")));
        Assertions.assertTrue(client.inventory().stream().noneMatch(item -> item.is("dirt")),
                "client inventory after the craft");
    }
}
