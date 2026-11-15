package io.github.h7kz.proofstone.examples.badtests.mod;

import io.github.h7kz.proofstone.api.Agent;
import io.github.h7kz.proofstone.api.CellTest;
import io.github.h7kz.proofstone.api.Matrix;
import io.github.h7kz.proofstone.api.Platform;
import io.github.h7kz.proofstone.api.Proofstone;

/**
 * A test on a Fabric cell whose mod under test crashes at load (T-M8-11, T-M8-13). The test declares the mod through
 * {@code @Proofstone(mods)}: the Fabric agent's {@code exampleCrashModJar}, the example mod with a marker resource that makes
 * it throw at init, copied here by the build. The loader gives up at start, and the harness must name the mod: the test is
 * {@code INVALID} with the mod's id, its file and the loader's line, never a boot or join timeout, and its body never runs.
 * The body fails if it does run: a mod that crashes and still lets the test pass would show here as a plain failure, and the
 * meta run reads it as a fault that was not caught. Excluded from every suite except {@code metaModTest}.
 *
 * <p>Pinned to {@code fabric@26.3} (examples/bad-tests/README.md); {@link BrokenNeoForgeModE2E} is the NeoForge row's.
 */
@Proofstone(mods = "build/fixture-mods/fabric/proofstone-crashing-mod.jar")
@Matrix(platform = Platform.FABRIC, versions = {"26.3"})
class BrokenFabricModE2E {

    @CellTest
    void aModThatCrashesAtLoadNeverGivesAVerdict(Agent agent) {
        throw new AssertionError("the body ran: the server started although the mod under test crashed at load, so the "
                + "fault was not caught");
    }
}
