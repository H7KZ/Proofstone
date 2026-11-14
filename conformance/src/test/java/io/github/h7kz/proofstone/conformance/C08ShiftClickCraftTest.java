package io.github.h7kz.proofstone.conformance;

import io.github.h7kz.proofstone.api.Agent;
import io.github.h7kz.proofstone.api.Cell;
import io.github.h7kz.proofstone.api.CellTest;
import io.github.h7kz.proofstone.api.Client;
import io.github.h7kz.proofstone.api.Matrix;
import io.github.h7kz.proofstone.api.Platform;
import io.github.h7kz.proofstone.api.Proofstone;
import io.github.h7kz.proofstone.core.conformance.C08ShiftClickCraft;

/** C08 shift-click craft. The scenario is in proofstone-core (T-M4-10a). */
@Proofstone
@Matrix(platform = Platform.PAPER, versions = {"latest"})
@Matrix(platform = Platform.FOLIA, versions = {"latest"})
@Matrix(platform = Platform.PURPUR, versions = {"latest"})
@Matrix(platform = Platform.SPIGOT, versions = {"latest"})
// Loader rows are experimental, so they run only when -Dproofstone.cells names them (fabric@26.3), T-M8-08.
@Matrix(platform = Platform.FABRIC, versions = {"26.3"})
@Matrix(platform = Platform.NEOFORGE, versions = {"26.3"})
class C08ShiftClickCraftTest {

    @CellTest
    void shiftClickMovesTheCraftingResultIntoTheInventory(Cell cell, Agent agent, Client client) {
        C08ShiftClickCraft.run(cell, agent, client);
    }
}
