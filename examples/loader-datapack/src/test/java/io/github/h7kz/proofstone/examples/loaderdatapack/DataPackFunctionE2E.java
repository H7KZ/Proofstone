package io.github.h7kz.proofstone.examples.loaderdatapack;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.h7kz.proofstone.api.Agent;
import io.github.h7kz.proofstone.api.CellTest;
import io.github.h7kz.proofstone.api.Client;
import io.github.h7kz.proofstone.api.DualAssert;
import io.github.h7kz.proofstone.api.Matrix;
import io.github.h7kz.proofstone.api.Platform;
import io.github.h7kz.proofstone.api.PlayerHandle;
import io.github.h7kz.proofstone.api.Proofstone;
import java.util.regex.Pattern;

/**
 * A data pack under test on one loader row, {@code fabric@26.3}. The pack is copied into the world before the server starts
 * and is never reloaded while the test runs. The test runs its function and reads the result on both oracles: the agent and
 * the client see the same five emeralds, and only the client sees the chat line. It is C-D01 of the conformance suite, cut
 * down to a user-sized test.
 *
 * <p>Loader rows are experimental, so the class plans a cell only when {@code -Dproofstone.cells=fabric@26.3} names it
 * (examples/loader-datapack/README.md).
 */
@Proofstone(datapacks = "src/test/datapacks/example-pack")
@Matrix(platform = Platform.FABRIC, versions = {"26.3"})
class DataPackFunctionE2E {

    @CellTest
    void theFunctionGivesFiveEmeraldsAndSaysSo(Agent agent, Client client) {
        PlayerHandle player = agent.player(client).reset();
        player.awaitState("empty inventory", state -> state.inventory().isEmpty());
        assertThat(client.inventory()).noneMatch(item -> item.is("emerald"));

        agent.command("function example_pack:hello");

        DualAssert.item(player, client).inInventory(item -> item.is("emerald")).amount(5);
        assertThat(client.awaitMessage(Pattern.compile("example-pack function ran"))).contains("function ran");
    }
}
