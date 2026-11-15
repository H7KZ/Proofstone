package io.github.h7kz.proofstone.examples.loadermod;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.h7kz.proofstone.api.Agent;
import io.github.h7kz.proofstone.api.CellTest;
import io.github.h7kz.proofstone.api.Client;
import io.github.h7kz.proofstone.api.Matrix;
import io.github.h7kz.proofstone.api.Platform;
import io.github.h7kz.proofstone.api.PlayerHandle;
import io.github.h7kz.proofstone.api.Proofstone;

/**
 * A server-only mod under test on a loader row (T-M8-13). The mod is the repository's own example mod, built by the loader
 * agent's {@code exampleModJar} and copied to {@code build/example-mod} by this module's build (the example's task picks the
 * loader from {@code -Dproofstone.cells}), so no artefact is downloaded. It registers nothing: a vanilla client can join a
 * server that runs it.
 *
 * <p>The harness copies the jar to the server's {@code /mods} before start and checks, after boot, that the agent lists the
 * mod's id ({@code proofstone_example}); a mod that did not load makes the test INVALID, naming it, before the body runs. The
 * body then shows the cell is usable with the mod in: the agent reports the {@code mods} capability and a vanilla client
 * joins and is seen by the agent.
 *
 * <p>Loader rows are experimental, so the class plans a cell only when {@code -Dproofstone.cells} names it
 * (examples/loader-mod/README.md).
 */
@Proofstone(mods = "build/example-mod/proofstone-example-mod.jar")
@Matrix(platform = Platform.FABRIC, versions = {"26.3"})
@Matrix(platform = Platform.NEOFORGE, versions = {"26.3"})
class ServerOnlyModE2E {

    @CellTest
    void aVanillaClientJoinsAServerThatRunsTheMod(Agent agent, Client client) {
        assertThat(agent.capabilities()).contains("mods");

        PlayerHandle player = agent.player(client);

        assertThat(player.awaitState("online", state -> state.name().equals(client.name())).name()).isEqualTo(client.name());
    }
}
