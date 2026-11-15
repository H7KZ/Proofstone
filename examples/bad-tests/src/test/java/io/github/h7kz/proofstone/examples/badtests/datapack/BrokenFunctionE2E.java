package io.github.h7kz.proofstone.examples.badtests.datapack;

import io.github.h7kz.proofstone.api.Agent;
import io.github.h7kz.proofstone.api.CellTest;
import io.github.h7kz.proofstone.api.Matrix;
import io.github.h7kz.proofstone.api.Platform;
import io.github.h7kz.proofstone.api.Proofstone;

/**
 * A test whose data pack holds a function that does not parse (T-M8-11). The server still enables the pack, so the
 * declared-pack check alone passes it; the server logs the function's parse error while it loads the pack. The harness must
 * read that line and make the test {@code INVALID}, naming the pack and quoting the server's message, before the body runs.
 * The body fails if it does run: a broken function that gets a verdict would show as a plain failure, and the meta run reads
 * it as a fault that was not caught. Excluded from every suite except {@code metaModTest}.
 *
 * <p>Pinned to {@code paper@26.3}, the row whose data-pack log lines were read (examples/bad-tests/README.md).
 */
@Proofstone(datapacks = "src/test/datapacks/broken-function")
@Matrix(platform = Platform.PAPER, versions = {"26.3"})
class BrokenFunctionE2E {

    @CellTest
    void aDataPackWithABrokenFunctionNeverGivesAVerdict(Agent agent) {
        throw new AssertionError("the body ran: the server logged a parse error for the data pack's function and the test "
                + "was still judged, so the fault was not caught");
    }
}
