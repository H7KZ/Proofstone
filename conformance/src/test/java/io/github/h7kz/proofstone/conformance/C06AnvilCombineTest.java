package io.github.h7kz.proofstone.conformance;

import io.github.h7kz.proofstone.api.Agent;
import io.github.h7kz.proofstone.api.Cell;
import io.github.h7kz.proofstone.api.CellTest;
import io.github.h7kz.proofstone.api.Client;
import io.github.h7kz.proofstone.api.Matrix;
import io.github.h7kz.proofstone.api.Platform;
import io.github.h7kz.proofstone.api.Proofstone;
import io.github.h7kz.proofstone.core.conformance.C06AnvilCombine;

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
