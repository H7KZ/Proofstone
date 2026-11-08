package dev.proofstone.conformance;

import dev.proofstone.api.Agent;
import dev.proofstone.api.Cell;
import dev.proofstone.api.CellTest;
import dev.proofstone.api.Client;
import dev.proofstone.api.Matrix;
import dev.proofstone.api.Platform;
import dev.proofstone.api.Proofstone;
import dev.proofstone.core.conformance.C04ChestMove;

/** C04 chest move. The scenario is in proofstone-core (T-M4-10a). */
@Proofstone
@Matrix(platform = Platform.PAPER, versions = {"latest"})
@Matrix(platform = Platform.FOLIA, versions = {"latest"})
@Matrix(platform = Platform.PURPUR, versions = {"latest"})
@Matrix(platform = Platform.SPIGOT, versions = {"latest"})
class C04ChestMoveTest {

    @CellTest
    void aStackMovesFromOneChestSlotToAnotherAndLeavesTheInventory(Cell cell, Agent agent, Client client) {
        C04ChestMove.run(cell, agent, client);
    }
}
