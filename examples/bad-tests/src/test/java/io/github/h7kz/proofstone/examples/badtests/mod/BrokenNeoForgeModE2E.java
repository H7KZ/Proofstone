package io.github.h7kz.proofstone.examples.badtests.mod;

import io.github.h7kz.proofstone.api.Agent;
import io.github.h7kz.proofstone.api.CellTest;
import io.github.h7kz.proofstone.api.Matrix;
import io.github.h7kz.proofstone.api.Platform;
import io.github.h7kz.proofstone.api.Proofstone;

/**
 * {@link BrokenFabricModE2E} on the NeoForge row (T-M8-13): the NeoForge agent's {@code exampleCrashModJar} throws in its
 * constructor, FML gives up, and the test is {@code INVALID} naming the mod. Pinned to {@code neoforge@26.3}. Excluded from
 * every suite except {@code metaModTest}.
 */
@Proofstone(mods = "build/fixture-mods/neoforge/proofstone-crashing-mod.jar")
@Matrix(platform = Platform.NEOFORGE, versions = {"26.3"})
class BrokenNeoForgeModE2E {

    @CellTest
    void aModThatCrashesAtLoadNeverGivesAVerdict(Agent agent) {
        throw new AssertionError("the body ran: the server started although the mod under test crashed at load, so the "
                + "fault was not caught");
    }
}
