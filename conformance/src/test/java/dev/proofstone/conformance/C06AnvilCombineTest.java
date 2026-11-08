package dev.proofstone.conformance;

import dev.proofstone.api.Agent;
import dev.proofstone.api.Cell;
import dev.proofstone.api.CellTest;
import dev.proofstone.api.Client;
import dev.proofstone.api.Matrix;
import dev.proofstone.api.Platform;
import dev.proofstone.api.Proofstone;
import dev.proofstone.core.conformance.C06AnvilCombine;

/** C06 anvil combine. The scenario is in proofstone-core (T-M4-10a). */
@Proofstone
@Matrix(platform = Platform.PAPER, versions = {"latest"})
@Matrix(platform = Platform.FOLIA, versions = {"latest"})
@Matrix(platform = Platform.PURPUR, versions = {"latest"})
@Matrix(platform = Platform.SPIGOT, versions = {"latest"})
class C06AnvilCombineTest {

    @CellTest
    void combiningASwordWithABookGivesTheVanillaResult(Cell cell, Agent agent, Client client) {
        C06AnvilCombine.run(cell, agent, client);
    }
}
