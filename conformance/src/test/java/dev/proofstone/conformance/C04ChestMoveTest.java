package dev.proofstone.conformance;

import static org.assertj.core.api.Assertions.assertThat;

import dev.proofstone.api.Agent;
import dev.proofstone.api.CellTest;
import dev.proofstone.api.Click;
import dev.proofstone.api.Client;
import dev.proofstone.api.Item;
import dev.proofstone.api.ItemView;
import dev.proofstone.api.Matrix;
import dev.proofstone.api.PlayerHandle;
import dev.proofstone.api.Platform;
import dev.proofstone.api.Proofstone;
import dev.proofstone.api.Window;
import java.util.Map;

/**
 * C04 (specs/self-tests.md §1): a stack moves from chest slot A to chest slot B through server-answered clicks. The
 * chest's own contents have no server oracle in v0 (there is no openWindow endpoint yet), so the dual check is on
 * the player's inventory: the stack must leave it on both oracles.
 */
@Proofstone
@Matrix(platform = Platform.PAPER, versions = {"latest"})
@Matrix(platform = Platform.FOLIA, versions = {"latest"})
class C04ChestMoveTest {

    private static final int SLOT_A = 0;
    private static final int SLOT_B = 13;

    @CellTest
    void aStackMovesFromOneChestSlotToAnotherAndLeavesTheInventory(Agent agent, Client client) {
        PlayerHandle player = ConformanceFixture.stand(agent, client);
        player.give(Item.of("diamond_sword").enchant("sharpness", 5));

        try (Window chest = client.openBlock(ConformanceFixture.CHEST)) {
            assertThat(chest.type()).isEqualTo("minecraft:generic_9x3");
            chest.moveFromInventory(ConformanceFixture.sword(), SLOT_A);
            chest.awaitSlot(SLOT_A, ConformanceFixture.sword());

            chest.click(SLOT_A, Click.LEFT);
            assertThat(chest.cursor().is("diamond_sword")).as("cursor after picking up slot A").isTrue();
            chest.click(SLOT_B, Click.LEFT);

            ItemView moved = chest.awaitSlot(SLOT_B, ConformanceFixture.sword());
            assertThat(moved.enchants()).isEqualTo(Map.of("minecraft:sharpness", 5));
            assertThat(chest.slot(SLOT_A).isEmpty()).as("slot A after the move").isTrue();
            assertThat(chest.cursor().isEmpty()).as("cursor after placing").isTrue();
        }

        // The stack is in the chest now, so it is in neither oracle's player inventory.
        player.awaitState("sword left the inventory",
                state -> state.inventory().values().stream().noneMatch(ConformanceFixture.sword()::test));
        assertThat(client.inventory()).as("client inventory after closing the chest")
                .noneMatch(ConformanceFixture.sword()::test);
    }
}
