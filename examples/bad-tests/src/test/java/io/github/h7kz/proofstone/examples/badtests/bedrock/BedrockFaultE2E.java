package io.github.h7kz.proofstone.examples.badtests.bedrock;

import io.github.h7kz.proofstone.api.Capability;
import io.github.h7kz.proofstone.api.CellTest;
import io.github.h7kz.proofstone.api.Clients;
import io.github.h7kz.proofstone.api.Matrix;
import io.github.h7kz.proofstone.api.Platform;
import io.github.h7kz.proofstone.api.Proofstone;
import io.github.h7kz.proofstone.api.Requires;

/**
 * A Bedrock test whose body would pass, on a Geyser cell that is broken on purpose (T-M9-11,
 * {@code -Dproofstone.injectGeyserFault}). The cell's first check (one Bedrock join, then the stand-in and the name guard) must
 * come back red, so the test is {@code INVALID} and its body never runs. The body fails if it does run: a fault that passed the
 * check would show here as a plain failure, and the meta run reads it as a fault that was not caught. {@code metaBedrockTest}
 * checks the message of each fault for its reason and hop (examples/bad-tests/build.gradle.kts). Pinned to the Geyser row,
 * refused on any other cell, and excluded from every suite except the meta run.
 */
@Proofstone
@Matrix(platform = Platform.GEYSER, versions = {"latest"})
@Requires(Capability.BEDROCK_CLIENT)
class BedrockFaultE2E {

    @CellTest
    void aTestOnABrokenGeyserCellNeverRuns(Clients clients) {
        throw new AssertionError("the body ran: the Geyser cell's check passed with an injected fault, so the fault was not caught");
    }
}
