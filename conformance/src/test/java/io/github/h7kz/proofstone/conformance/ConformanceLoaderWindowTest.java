package io.github.h7kz.proofstone.conformance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import io.github.h7kz.proofstone.api.Agent;
import io.github.h7kz.proofstone.api.CellTest;
import io.github.h7kz.proofstone.api.Client;
import io.github.h7kz.proofstone.api.Matrix;
import io.github.h7kz.proofstone.api.Platform;
import io.github.h7kz.proofstone.api.PlayerHandle;
import io.github.h7kz.proofstone.api.Proofstone;
import io.github.h7kz.proofstone.api.Window;
import io.github.h7kz.proofstone.core.conformance.ConformanceFixture;

/**
 * The window quirk of the loader agents (T-M8-08), asserted as it is. A vanilla server does not keep an open window's
 * title (it is sent to the client once, at open), so the Fabric and NeoForge agents cannot report a player's state while a
 * window is open: the read is {@code 501 NOT_SUPPORTED}, a loud error and never an invented title (docs/platforms/fabric.md
 * and neoforge.md, Gotchas). The Bukkit agent answers the same read, so this class declares loader rows only.
 *
 * <p>C04 to C08 read the player's state after the window is closed, which both loader agents answer, so they run
 * unchanged. This test pins the one read they cannot do, so a later agent that starts answering it turns this red and the
 * quirk is removed from the docs on purpose.
 */
@Proofstone
@Matrix(platform = Platform.FABRIC, versions = {"26.3"})
@Matrix(platform = Platform.NEOFORGE, versions = {"26.3"})
class ConformanceLoaderWindowTest {

    @CellTest
    void aPlayerStateReadWithAWindowOpenIsNotSupportedAndAnsweredAgainOnceItIsClosed(Agent agent, Client client) {
        PlayerHandle player = ConformanceFixture.stand(agent, client);

        try (Window chest = client.openBlock(ConformanceFixture.CHEST)) {
            assertThat(chest.type()).isEqualTo("minecraft:generic_9x3");
            Throwable refused = catchThrowable(player::state);
            assertThat(refused).as("the player state read with a window open").isNotNull();
            assertThat(refused.getMessage()).contains("NOT_SUPPORTED").contains("window open");
        }

        // Closed: the same read is answered (the server thread closed the menu, so the player is back on its own inventory).
        assertThat(player.awaitState("the window is closed", state -> state.inventory().isEmpty()).name())
                .isEqualTo(client.name());
    }
}
