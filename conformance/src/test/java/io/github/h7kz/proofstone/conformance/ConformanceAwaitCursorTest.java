package io.github.h7kz.proofstone.conformance;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.h7kz.proofstone.api.Agent;
import io.github.h7kz.proofstone.api.CellTest;
import io.github.h7kz.proofstone.api.Click;
import io.github.h7kz.proofstone.api.Client;
import io.github.h7kz.proofstone.api.Item;
import io.github.h7kz.proofstone.api.ItemView;
import io.github.h7kz.proofstone.api.Matrix;
import io.github.h7kz.proofstone.api.Platform;
import io.github.h7kz.proofstone.api.PlayerHandle;
import io.github.h7kz.proofstone.api.Proofstone;
import io.github.h7kz.proofstone.api.Window;
import io.github.h7kz.proofstone.core.conformance.ConformanceFixture;

/**
 * C-W03 (specs/self-tests.md section 1, T-M11-11): {@code Window.awaitCursor} returns the client's cursor once it holds
 * what the predicate accepts. An enchanted sword is picked up from a chest slot; the pick-up click returns on the first
 * window sync, so the cursor is read through {@code awaitCursor} and not with a one-shot read. A second call on the
 * already-satisfied cursor returns at once with the same item.
 */
@Proofstone
@Matrix(platform = Platform.PAPER, versions = {"latest"})
class ConformanceAwaitCursorTest {

    private static final int SLOT = 0;

    @CellTest
    void awaitCursorSeesTheItemPickedUpFromAChestSlot(Agent agent, Client client) {
        PlayerHandle player = ConformanceFixture.stand(agent, client);
        player.give(Item.of("diamond_sword").enchant("sharpness", 5));

        try (Window chest = client.openBlock(ConformanceFixture.CHEST)) {
            chest.moveFromInventory(ConformanceFixture.sword(), SLOT);
            chest.awaitSlot(SLOT, ConformanceFixture.sword());

            chest.click(SLOT, Click.LEFT);
            ItemView cursor = chest.awaitCursor(item -> item.is("diamond_sword"));

            assertThat(cursor.enchants()).as("enchants on the awaited cursor").containsEntry("minecraft:sharpness", 5);
            assertThat(chest.awaitCursor(item -> item.is("diamond_sword")).type()).as("an already-satisfied cursor")
                    .isEqualTo("minecraft:diamond_sword");
            assertThat(chest.slot(SLOT).isEmpty()).as("the picked-up slot").isTrue();
        }
    }
}
