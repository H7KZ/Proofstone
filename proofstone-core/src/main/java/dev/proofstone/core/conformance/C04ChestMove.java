package dev.proofstone.core.conformance;

import dev.proofstone.api.Agent;
import dev.proofstone.api.Cell;
import dev.proofstone.api.Click;
import dev.proofstone.api.Client;
import dev.proofstone.api.Item;
import dev.proofstone.api.ItemView;
import dev.proofstone.api.PlayerHandle;
import dev.proofstone.api.Window;
import java.util.Map;
import org.junit.jupiter.api.Assertions;

/**
 * C04 (specs/self-tests.md §1): a stack moves from chest slot A to chest slot B through server-answered clicks. The
 * chest's own contents have no server oracle in v0 (there is no openWindow endpoint yet), so the dual check is on
 * the player's inventory: the stack must leave it on both oracles.
 */
public final class C04ChestMove {

    private static final String CHEST_TYPE = "minecraft:generic_9x3";
    /** What the window fault expects instead: a chest the vanilla server never opens (T-M4-10b). */
    private static final String FAULT_CHEST_TYPE = "minecraft:generic_9x2";
    private static final int SLOT_A = 0;
    private static final int SLOT_B = 13;

    private C04ChestMove() {
    }

    /** Runs the scenario on a joined client. Throws an {@link AssertionError} when an oracle disagrees. */
    public static void run(Cell cell, Agent agent, Client client) {
        run(cell, agent, client, false);
    }

    /**
     * @param windowFault {@code true} is the conformance-breaking override of the per-cell check (T-M4-10b): the
     *     scenario expects a chest type the server never opens, so the cell fails and its tests are INVALID
     */
    public static void run(Cell cell, Agent agent, Client client, boolean windowFault) {
        PlayerHandle player = ConformanceFixture.stand(agent, client);
        player.give(Item.of("diamond_sword").enchant("sharpness", 5));

        try (Window chest = client.openBlock(ConformanceFixture.CHEST)) {
            Assertions.assertEquals(windowFault ? FAULT_CHEST_TYPE : CHEST_TYPE, chest.type(), "chest window type");
            chest.moveFromInventory(ConformanceFixture.sword(), SLOT_A);
            chest.awaitSlot(SLOT_A, ConformanceFixture.sword());

            chest.click(SLOT_A, Click.LEFT);
            Assertions.assertTrue(chest.cursor().is("diamond_sword"), "cursor after picking up slot A");
            chest.click(SLOT_B, Click.LEFT);

            ItemView moved = chest.awaitSlot(SLOT_B, ConformanceFixture.sword());
            Assertions.assertEquals(Map.of("minecraft:sharpness", 5), moved.enchants(), "enchants after the move");
            Assertions.assertTrue(chest.slot(SLOT_A).isEmpty(), "slot A after the move");
            Assertions.assertTrue(chest.cursor().isEmpty(), "cursor after placing");
        }

        // The stack is in the chest now, so it is in neither oracle's player inventory.
        player.awaitState("sword left the inventory",
                state -> state.inventory().values().stream().noneMatch(ConformanceFixture.sword()::test));
        Assertions.assertTrue(client.inventory().stream().noneMatch(ConformanceFixture.sword()::test),
                "client inventory after closing the chest");
    }
}
