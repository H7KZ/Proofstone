package io.github.h7kz.proofstone.core.conformance;

import io.github.h7kz.proofstone.api.Agent;
import io.github.h7kz.proofstone.api.Cell;
import io.github.h7kz.proofstone.api.Click;
import io.github.h7kz.proofstone.api.Client;
import io.github.h7kz.proofstone.api.DualAssert;
import io.github.h7kz.proofstone.api.Item;
import io.github.h7kz.proofstone.api.ItemView;
import io.github.h7kz.proofstone.api.PlayerHandle;
import io.github.h7kz.proofstone.api.Window;
import org.junit.jupiter.api.Assertions;

/**
 * C05 (specs/self-tests.md §1): a vanilla anvil rename. The result slot shows the new name, taking it costs one
 * experience level (vanilla charges one level for a rename with no other work), and the experience sync reaches both
 * oracles.
 */
public final class C05AnvilRenameCost {

    private static final int START_LEVEL = 10;
    private static final int RENAME_COST = 1;

    private C05AnvilRenameCost() {
    }

    /** Runs the scenario on a joined client. Throws an {@link AssertionError} when an oracle disagrees. */
    public static void run(Cell cell, Agent agent, Client client) {
        PlayerHandle player = ConformanceFixture.stand(agent, client);
        player.give(Item.of("diamond_sword")).level(START_LEVEL);
        DualAssert.level(player, client).isEqualTo(START_LEVEL);

        try (Window anvil = client.openBlock(ConformanceFixture.ANVIL)) {
            Assertions.assertEquals("minecraft:anvil", anvil.type(), "anvil window type");
            anvil.moveFromInventory(ConformanceFixture.sword(), 0);
            anvil.awaitSlot(0, ConformanceFixture.sword());

            anvil.rename("Blade");
            ItemView result = anvil.awaitSlot(2, item -> item.is("diamond_sword") && "Blade".equals(item.name()));
            Assertions.assertEquals("Blade", result.name(), "name of the result slot");

            anvil.click(2, Click.LEFT);
            Assertions.assertEquals("Blade", anvil.cursor().name(), "the renamed sword on the cursor");
            ConformanceFixture.placeCursorInInventory(anvil);
        }

        DualAssert.item(player, client).inInventory(ConformanceFixture.sword()).amount(1).name("Blade");
        DualAssert.level(player, client).isEqualTo(START_LEVEL - RENAME_COST);
    }
}
