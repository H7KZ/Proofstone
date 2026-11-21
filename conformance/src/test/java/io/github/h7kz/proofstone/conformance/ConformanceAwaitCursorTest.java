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
 * what the predicate accepts. An enchanted sword is picked up from a chest slot and read through {@code awaitCursor}; a
 * second call on the already-satisfied cursor returns at once with the same item.
 *
 * <p>Limit: this proves the call works against a real server and sees the item with its enchantment. It cannot fail on
 * trailing-cursor semantics: on Paper the cursor packet arrives before the first read, so a one-shot {@code cursor()}
 * would pass here too. The late-arrival semantics are covered by {@code SessionWindowAwaitCursorTest}, with a cursor
 * delivered from another thread after the click returned.
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
