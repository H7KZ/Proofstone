package io.github.h7kz.proofstone.conformance;

import io.github.h7kz.proofstone.api.Agent;
import io.github.h7kz.proofstone.api.CellTest;
import io.github.h7kz.proofstone.api.Client;
import io.github.h7kz.proofstone.api.DualAssert;
import io.github.h7kz.proofstone.api.Matrix;
import io.github.h7kz.proofstone.api.Platform;
import io.github.h7kz.proofstone.api.PlayerHandle;
import io.github.h7kz.proofstone.api.Proofstone;
import io.github.h7kz.proofstone.core.conformance.ConformanceFixture;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Assertions;

/**
 * C-D01 (specs/self-tests.md section 1c): a data pack's function runs and its effects show on both oracles. The pack is
 * copied in before the server starts (ADR-0017 section 4) and never reloaded; the test only runs the function. The
 * function gives the player five emeralds (agent and client both read them) and tells the player so in chat (the client
 * sees it where the agent cannot). A cell without DATAPACKS_VANILLA skips with the capability named, which the declaration
 * implies, so neoforge@26.3 shows the skip.
 */
@Proofstone(datapacks = "src/test/datapacks/proofstone-e2e")
@Matrix(platform = Platform.PAPER, versions = {"latest"})
// Loader rows are experimental, so they run only when -Dproofstone.cells names them (fabric@26.3), T-M8-08.
@Matrix(platform = Platform.FABRIC, versions = {"26.3"})
@Matrix(platform = Platform.NEOFORGE, versions = {"26.3"})
class CD01DataPackFunctionTest {

    @CellTest
    void aDataPackFunctionRunsAndItsEffectsReadTheSameOnBothOracles(Agent agent, Client client) {
        PlayerHandle player = ConformanceFixture.stand(agent, client);
        Assertions.assertTrue(client.inventory().stream().noneMatch(item -> item.is("emerald")),
                "no emerald before the function runs");

        agent.command("function proofstone_e2e:hello");

        DualAssert.item(player, client).inInventory(item -> item.is("emerald")).amount(5);
        Assertions.assertTrue(client.awaitMessage(Pattern.compile("proofstone-e2e function ran")).contains("function ran"),
                "the chat line the function sent");
    }
}
