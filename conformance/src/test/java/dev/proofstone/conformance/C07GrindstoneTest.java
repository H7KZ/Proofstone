package dev.proofstone.conformance;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.proofstone.api.Agent;
import dev.proofstone.api.Cell;
import dev.proofstone.api.CellTest;
import dev.proofstone.api.Client;
import dev.proofstone.api.Matrix;
import dev.proofstone.api.Platform;
import dev.proofstone.api.Proofstone;
import dev.proofstone.core.conformance.C07Grindstone;

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
