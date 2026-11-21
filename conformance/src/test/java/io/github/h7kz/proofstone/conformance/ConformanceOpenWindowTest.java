package io.github.h7kz.proofstone.conformance;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.h7kz.proofstone.api.Agent;
import io.github.h7kz.proofstone.api.CellTest;
import io.github.h7kz.proofstone.api.Client;
import io.github.h7kz.proofstone.api.Item;
import io.github.h7kz.proofstone.api.ItemView;
import io.github.h7kz.proofstone.api.Matrix;
import io.github.h7kz.proofstone.api.PlayerHandle;
import io.github.h7kz.proofstone.api.PlayerState;
import io.github.h7kz.proofstone.api.Platform;
import io.github.h7kz.proofstone.api.Proofstone;
import io.github.h7kz.proofstone.api.ServerWindow;
import io.github.h7kz.proofstone.api.Window;
import io.github.h7kz.proofstone.core.conformance.ConformanceFixture;
import java.util.Map;

/**
 * C-W01 (specs/self-tests.md section 1, T-M11-02): {@code PlayerState.openWindow} is the server's view of the container a
 * player has open. No window before the client opens one; a chest window of 27 slots while it is open, holding what the
 * client moved in and keeping server order; no window again after the client closes it.
 */
@Proofstone
@Matrix(platform = Platform.PAPER, versions = {"latest"})
@Matrix(platform = Platform.FOLIA, versions = {"latest"})
@Matrix(platform = Platform.PURPUR, versions = {"latest"})
@Matrix(platform = Platform.SPIGOT, versions = {"latest"})
class ConformanceOpenWindowTest {

    private static final int SLOT = 13;

    @CellTest
    void theServerReportsTheOpenChestWithItsSlotsAndNoWindowAfterClose(Agent agent, Client client) {
        PlayerHandle player = ConformanceFixture.stand(agent, client);
        player.give(Item.of("diamond_sword").enchant("sharpness", 5));
        assertThat(player.state().openWindow()).as("no window before one is opened").isEmpty();

        try (Window chest = client.openBlock(ConformanceFixture.CHEST)) {
            chest.moveFromInventory(ConformanceFixture.sword(), SLOT);
            chest.awaitSlot(SLOT, ConformanceFixture.sword());

            PlayerState open = player.awaitState("the chest window with the sword in slot " + SLOT,
                    state -> state.openWindow().map(w -> w.slots().get(SLOT).is("diamond_sword")).orElse(false));
            ServerWindow window = open.openWindow().orElseThrow();
            assertThat(window.type()).isEqualTo("chest");
            assertThat(window.title()).isEqualTo("Chest");
            assertThat(window.slots()).as("the chest's own 27 slots, without the player's").hasSize(27);
            assertThat(window.slots().get(SLOT).enchants()).isEqualTo(Map.of("minecraft:sharpness", 5));
            assertThat(window.slots().get(0)).as("an empty slot keeps its place").isEqualTo(ItemView.EMPTY);
        }

        player.awaitState("no window after the client closed the chest", state -> state.openWindow().isEmpty());
    }
}
