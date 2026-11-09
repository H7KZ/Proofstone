package io.github.h7kz.proofstone.conformance;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.github.h7kz.proofstone.api.Agent;
import io.github.h7kz.proofstone.api.Cell;
import io.github.h7kz.proofstone.api.CellTest;
import io.github.h7kz.proofstone.api.Client;
import io.github.h7kz.proofstone.api.Matrix;
import io.github.h7kz.proofstone.api.Platform;
import io.github.h7kz.proofstone.api.Proofstone;
import io.github.h7kz.proofstone.core.conformance.C07Grindstone;

/** C07 grindstone. The scenario is in proofstone-core (T-M4-10a). */
@Proofstone
@Matrix(platform = Platform.PAPER, versions = {"latest"})
@Matrix(platform = Platform.FOLIA, versions = {"latest"})
@Matrix(platform = Platform.PURPUR, versions = {"latest"})
@Matrix(platform = Platform.SPIGOT, versions = {"latest"})
class C07GrindstoneTest {

    @CellTest
    void grindstoneRemovesTheEnchantsOnBothOracles(Cell cell, Agent agent, Client client) {
        assumeTrue(C07Grindstone.hasGrindstone(cell.version()), "grindstone arrived in 1.14; " + cell.version()
                + " has no grindstone, so C07 cannot run there (docs/platforms/paper.md)");
        C07Grindstone.run(cell, agent, client);
    }
}
