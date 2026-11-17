package io.github.h7kz.proofstone.core.junit;

import io.github.h7kz.proofstone.api.Proofstone;
import java.util.Optional;

/**
 * What a Geyser cell refuses before anything starts (ADR-0018 sections 3 and 9, test-api spec section 14). A refusal is an
 * error that names the cell, never a skip and never a pass.
 */
final class GeyserRun {

    private GeyserRun() {
    }

    /**
     * Why a run of the Geyser cell {@code cellId} is refused, or empty when it may start.
     *
     * @param differential whether the run is a differential one (self-tests section 3)
     * @param repeats N of the flake detector; 1 when it is off
     * @param driverId the client driver of the run
     * @param primaryDriver the id of the native Java driver, the only one a Geyser cell joins its Java client with
     * @param bridged whether the Java driver's protocol differs from the cell's row, so the join would go through ViaProxy
     */
    static Optional<String> refusal(String cellId, boolean differential, int repeats, String driverId,
            String primaryDriver, boolean bridged) {
        if (differential || repeats > 1) {
            return Optional.of("INVALID: a differential run or the flake detector on the Geyser cell " + cellId
                    + " is refused: there is no second Bedrock driver to compare with and a Bedrock join is judged once"
                    + " per cell (ADR-0018 section 9); run it without -Dproofstone.differential and -Dproofstone.repeat");
        }
        if (!primaryDriver.equals(driverId)) {
            return Optional.of("INVALID: driver " + driverId + " cannot run on the Geyser cell " + cellId
                    + ": only the native " + primaryDriver + " driver joins its Java client (ADR-0018 section 3)");
        }
        if (bridged) {
            return Optional.of("INVALID: the Geyser cell " + cellId + " would need ViaProxy for the Java driver; a cell is "
                    + "native or it is not planned, and ViaProxy is never put in front of a Geyser cell (ADR-0018 section 3)");
        }
        return Optional.empty();
    }

    /**
     * Why the class's declaration is invalid on a Geyser cell, or empty. A Geyser cell loads plugins as a Paper cell does;
     * it loads no mods and no data pack was run behind Geyser, so both are refused rather than silently ignored.
     */
    static Optional<String> declarationRefusal(String className, Proofstone config) {
        if (config == null) {
            return Optional.empty();
        }
        if (config.mods().length > 0) {
            return Optional.of("INVALID test declaration: " + className + " declares mods on a Geyser cell, which loads "
                    + "none (ADR-0018 section 8); remove mods from @Proofstone for this class");
        }
        if (config.datapacks().length > 0) {
            return Optional.of("INVALID test declaration: " + className + " declares datapacks on a Geyser cell, where no "
                    + "data pack run has shown them loading (ADR-0018 section 8); remove datapacks from @Proofstone for "
                    + "this class");
        }
        return Optional.empty();
    }
}
