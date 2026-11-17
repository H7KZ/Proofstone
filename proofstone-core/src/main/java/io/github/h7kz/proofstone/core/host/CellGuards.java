package io.github.h7kz.proofstone.core.host;

import java.util.ArrayList;
import java.util.List;

/**
 * The guards of a Geyser cell's stand-in and driver host over the cell's whole life (ADR-0018 sections 4 and 6, T-M10-06): the
 * name guard and the {@link AuxLogGuard} on both containers' logs. Each {@link #requireClean} judges only what was logged since
 * the previous call, so the test that asked a denied name or caused an error is the one that fails, and the next test is not
 * failed again for the same line. The first call judges the whole log.
 */
final class CellGuards {

    private final LogBuffer standIn;
    private final LogBuffer driverHost;
    private final boolean refusalExpected;
    private int standInMark;
    private int driverMark;

    /** @param refusalExpected true only in the {@code stand-in-absent} fault, where {@code STUB refused} is the point */
    CellGuards(LogBuffer standIn, LogBuffer driverHost, boolean refusalExpected) {
        this.standIn = standIn;
        this.driverHost = driverHost;
        this.refusalExpected = refusalExpected;
    }

    /** @throws PaperHostException naming every problem logged since the last call, with the log tails */
    synchronized void requireClean() {
        List<String> standInLines = standIn.since(standInMark);
        List<String> driverLines = driverHost.since(driverMark);
        standInMark += standInLines.size();
        driverMark += driverLines.size();
        List<String> problems = new ArrayList<>();
        List<String> names = NameGuard.violations(String.join("\n", standInLines));
        if (!names.isEmpty()) {
            problems.add("the Geyser cell looked up a name the guard refuses: " + names);
        }
        String standInProblem = AuxLogGuard.violations("stand-in", standInLines, AuxLogGuard.STAND_IN_ALLOWLIST, refusalExpected);
        if (standInProblem != null) {
            problems.add(standInProblem);
        }
        String driverProblem = AuxLogGuard.violations("driver host", driverLines, AuxLogGuard.DRIVER_HOST_ALLOWLIST, false);
        if (driverProblem != null) {
            problems.add(driverProblem);
        }
        if (!problems.isEmpty()) {
            throw new PaperHostException(String.join("\n", problems) + "\n--- stand-in log tail ---\n" + standIn.tail(TAIL)
                    + "\n--- driver host log tail ---\n" + driverHost.tail(TAIL));
        }
    }

    private static final int TAIL = 30;
}
