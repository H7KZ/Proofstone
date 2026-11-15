package io.github.h7kz.proofstone.examples.badtests.mod;

import io.github.h7kz.proofstone.api.Agent;
import io.github.h7kz.proofstone.api.CellTest;
import io.github.h7kz.proofstone.api.Matrix;
import io.github.h7kz.proofstone.api.Platform;
import io.github.h7kz.proofstone.api.Proofstone;

/**
 * A test on a Fabric cell whose mod under test crashes at load (T-M8-11). {@code -Dproofstone.injectModFault=crash-at-load}
 * puts the example mod in {@code /mods} with the config that makes it throw at init, so the loader gives up at start. The
 * harness must name that mod: the test is {@code INVALID} with the mod's id and the loader's line, never a boot or join
 * timeout, and its body never runs. The body fails if it does run: a mod that crashes and still lets the test pass would show
 * here as a plain failure, and the meta run reads it as a fault that was not caught. Excluded from every suite except
 * {@code metaModTest}.
 *
 * <p>Pinned to one loader row per task, {@code fabric@26.3} or {@code neoforge@26.3} (examples/bad-tests/README.md): the
 * injection needs a loader cell, and each row has its own example mod jar.
 */
@Proofstone
@Matrix(platform = Platform.FABRIC, versions = {"26.3"})
@Matrix(platform = Platform.NEOFORGE, versions = {"26.3"})
class BrokenModE2E {

    @CellTest
    void aModThatCrashesAtLoadNeverGivesAVerdict(Agent agent) {
        throw new AssertionError("the body ran: the server started although the mod under test crashed at load, so the "
                + "fault was not caught");
    }
}
