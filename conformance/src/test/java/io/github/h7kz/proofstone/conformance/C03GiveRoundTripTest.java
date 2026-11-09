package io.github.h7kz.proofstone.conformance;

import io.github.h7kz.proofstone.api.Agent;
import io.github.h7kz.proofstone.api.Cell;
import io.github.h7kz.proofstone.api.CellTest;
import io.github.h7kz.proofstone.api.Client;
import io.github.h7kz.proofstone.api.Matrix;
import io.github.h7kz.proofstone.api.Platform;
import io.github.h7kz.proofstone.api.Proofstone;
import io.github.h7kz.proofstone.core.conformance.C03GiveRoundTrip;

/** C03 `give` round trip. The scenario is in proofstone-core (T-M4-10a). */
@Proofstone
@Matrix(platform = Platform.PAPER, versions = {"latest"})
@Matrix(platform = Platform.FOLIA, versions = {"latest"})
@Matrix(platform = Platform.PURPUR, versions = {"latest"})
@Matrix(platform = Platform.SPIGOT, versions = {"latest"})
class C03GiveRoundTripTest {

    @CellTest
    void givenItemsReadTheSameOnBothOracles(Cell cell, Agent agent, Client client) {
        C03GiveRoundTrip.run(cell, agent, client);
    }
}
