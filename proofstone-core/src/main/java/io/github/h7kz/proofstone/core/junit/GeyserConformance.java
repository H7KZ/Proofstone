package io.github.h7kz.proofstone.core.junit;

import io.github.h7kz.proofstone.core.cell.GeyserCell;
import io.github.h7kz.proofstone.core.host.GeyserHost;
import io.github.h7kz.proofstone.core.host.PaperHostException;
import io.github.h7kz.proofstone.core.host.standin.StandIn;
import io.github.h7kz.proofstone.core.verdict.ConformanceResult;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * The first cached check of a Geyser cell (ADR-0018 sections 4 and 9, T-M9-08): one Bedrock client joins, and afterwards the
 * discovery stand-in must have answered exactly the two expected Microsoft names and the name guard must be clean. A red is a
 * failed conformance, so every test on the cell is INVALID. Geyser asks for the two names at its first login of each server start,
 * so the check needs a join; it is the cell's own, and the C-G scenarios then run on a cell known to be offline-stub.
 */
final class GeyserConformance {

    /** The Bedrock client the check joins with. Tests use their own names. */
    static final String CLIENT = "PSConfB";

    private GeyserConformance() {
    }

    /**
     * @param join joins a Bedrock client under the given name and returns it once the agent lists it
     * @param awaitGone waits, on the agent, until the server no longer lists {@code .name}
     */
    static ConformanceResult run(String cell, GeyserHost host, Function<String, SessionClient> join,
            Consumer<String> awaitGone) {
        // Before the join, so what the join logs is this check's own.
        long window = ErrorLogGuard.cursor(host.server());
        SessionClient client;
        try {
            client = join.apply(CLIENT);
        } catch (RuntimeException | AssertionError e) {
            String message = e.getMessage() == null ? e.getClass().getName() : e.getMessage();
            return new ConformanceResult(cell, false, "the Bedrock conformance client could not join: " + message);
        }
        String logged = null;
        try {
            ErrorLogGuard.check(host.server(), window, ErrorLogGuard.Patterns.of(GeyserCell.ID));
        } catch (AssertionError e) {
            logged = e.getMessage();
        }
        try {
            client.close();
            awaitGone.accept("." + CLIENT);
        } catch (RuntimeException e) {
            String message = e.getMessage() == null ? e.getClass().getName() : e.getMessage();
            return new ConformanceResult(cell, false, "the Bedrock conformance client could not leave: " + message);
        }
        if (logged != null) {
            return new ConformanceResult(cell, false, "the server logged errors during the Bedrock join: " + logged);
        }
        String guard = null;
        try {
            host.requireNameGuardClean();
        } catch (PaperHostException e) {
            guard = e.getMessage();
        }
        return judgeNames(cell, host.namesAnswered(), guard);
    }

    /**
     * @param answered the names the stand-in answered itself
     * @param guardFailure what the name guard refused, or {@code null} when it is clean
     */
    static ConformanceResult judgeNames(String cell, Set<String> answered, String guardFailure) {
        Set<String> missing = new TreeSet<>(StandIn.NAMES);
        missing.removeAll(answered);
        Set<String> unexpected = new TreeSet<>(answered);
        unexpected.removeAll(StandIn.NAMES);
        if (!missing.isEmpty() || !unexpected.isEmpty()) {
            return new ConformanceResult(cell, false, "the stand-in must be asked exactly the two expected names "
                    + new TreeSet<>(StandIn.NAMES) + " and answered " + new TreeSet<>(answered)
                    + (missing.isEmpty() ? "" : "; missing " + missing)
                    + (unexpected.isEmpty() ? "" : "; unexpected " + unexpected));
        }
        if (guardFailure != null) {
            return new ConformanceResult(cell, false, "the name guard is not clean: " + guardFailure);
        }
        return ConformanceResult.pass(cell);
    }
}
