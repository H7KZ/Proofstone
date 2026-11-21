package io.github.h7kz.proofstone.conformance;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.h7kz.proofstone.api.Agent;
import io.github.h7kz.proofstone.api.CellTest;
import io.github.h7kz.proofstone.api.Click;
import io.github.h7kz.proofstone.api.Client;
import io.github.h7kz.proofstone.api.DragMode;
import io.github.h7kz.proofstone.api.Item;
import io.github.h7kz.proofstone.api.ItemView;
import io.github.h7kz.proofstone.api.Matrix;
import io.github.h7kz.proofstone.api.Platform;
import io.github.h7kz.proofstone.api.PlayerHandle;
import io.github.h7kz.proofstone.api.PlayerState;
import io.github.h7kz.proofstone.api.Proofstone;
import io.github.h7kz.proofstone.api.Window;
import io.github.h7kz.proofstone.core.conformance.ConformanceFixture;
import java.util.List;

/**
 * C-W02 (specs/self-tests.md section 1, T-M11-09): {@code Window.drag} is a player's inventory drag and the server
 * distributes the carried stack as vanilla does. Ten stone on the cursor are dragged over three empty main-inventory slots
 * of an open chest window: a left drag splits them evenly (3, 3, 3, one stays on the cursor), a right drag puts one in each
 * (1, 1, 1, seven stay), a creative middle drag fills each slot to a full stack of 64 and the server takes the whole carried stack (the cursor is
 * empty, not ten). Both oracles read the result.
 */
@Proofstone
@Matrix(platform = Platform.PAPER, versions = {"latest"})
class ConformanceWindowDragTest {

    /** The first three main-inventory slots in a chest window (27 chest slots first); raw player slots 9 to 11. */
    private static final List<Integer> DRAG_SLOTS = List.of(27, 28, 29);
    private static final List<Integer> RAW_SLOTS = List.of(9, 10, 11);

    @CellTest
    void leftDragSplitsTheCarriedStackEvenly(Agent agent, Client client) {
        drag(agent, client, DragMode.LEFT, false, 3, 1);
    }

    @CellTest
    void rightDragPutsOneItemInEachSlot(Agent agent, Client client) {
        drag(agent, client, DragMode.RIGHT, false, 1, 7);
    }

    @CellTest
    void middleDragInCreativeFillsEachSlotToAFullStack(Agent agent, Client client) {
        drag(agent, client, DragMode.MIDDLE, true, 64, 0);
    }

    private static void drag(Agent agent, Client client, DragMode mode, boolean creative, int perSlot, int onCursor) {
        PlayerHandle player = ConformanceFixture.stand(agent, client);
        if (creative) {
            agent.command("gamemode creative " + client.name());
        }
        player.give(Item.of("stone").amount(10));
        player.awaitState("ten stone in the inventory", state -> state.inventory().values().stream()
                .anyMatch(item -> item.is("stone") && item.amount() == 10));

        try (Window chest = client.openBlock(ConformanceFixture.CHEST)) {
            int from = ConformanceFixture.playerSlot(chest, item -> item.is("stone"));
            chest.click(from, Click.LEFT);
            chest.awaitCursor(cursor -> cursor.amount() == 10);

            chest.drag(DRAG_SLOTS, mode);

            for (int slot : DRAG_SLOTS) {
                ItemView item = chest.awaitSlot(slot, view -> view.is("stone") && view.amount() == perSlot);
                assertThat(item.amount()).as("client slot " + slot + " after the " + mode + " drag").isEqualTo(perSlot);
            }
            assertThat(chest.cursor().amount()).as("client cursor after the " + mode + " drag").isEqualTo(onCursor);

            PlayerState state = player.awaitState("the " + mode + " drag on the server",
                    s -> RAW_SLOTS.stream().allMatch(raw -> s.inventory().containsKey(raw)
                            && s.inventory().get(raw).amount() == perSlot) && s.cursor().amount() == onCursor);
            for (int raw : RAW_SLOTS) {
                assertThat(state.inventory().get(raw).type()).as("server slot " + raw).isEqualTo("minecraft:stone");
            }
        }
    }
}
