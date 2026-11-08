package dev.proofstone.core.junit;

import dev.proofstone.core.host.PaperHost;
import dev.proofstone.core.verdict.ConformanceResult;

/**
 * The conformance run of one cell (self-tests section 1), run once per cell per run through {@code ConformanceCache}.
 * It is the first scenario set only: the vanilla {@code list} command answers on the console path the harness uses for
 * setup. The full suite (C01 to C12, conformance module) is not wired into the extension yet.
 *
 * <p>The switch {@code -Dproofstone.injectConformanceFault=true} is the conformance-breaking cell override. It changes
 * the text the scenario expects to one the vanilla server never prints, so the cell fails and every test on it is
 * {@code INVALID}. Only the bad-tests meta run sets it.
 */
final class CellConformance {

    /** The system property that turns on the conformance-breaking override. The Gradle build forwards it to the JVM. */
    static final String INJECT = "proofstone.injectConformanceFault";
    /** Part of what vanilla {@code list} prints on every row from 1.8.8 to 26.x. */
    static final String EXPECTED = "players online";
    static final String FAULT_EXPECTED = "PROOFSTONE-CONFORMANCE-FAULT";

    private CellConformance() {
    }

    static ConformanceResult run(String cell, PaperHost host) {
        String expected = injected() ? FAULT_EXPECTED : EXPECTED;
        String reply = host.command("list");
        if (reply == null || reply.isBlank()) {
            return new ConformanceResult(cell, false, "`list` gave a blank reply, so the console path is unverified");
        }
        if (!reply.contains(expected)) {
            return new ConformanceResult(cell, false, "`list` did not print \"" + expected + "\": " + reply.trim());
        }
        return ConformanceResult.pass(cell);
    }

    /** @return the override switch; a value other than {@code true} or {@code false} is an error, never a default */
    static boolean injected() {
        String value = System.getProperty(INJECT);
        if (value == null || value.equals("false")) {
            return false;
        }
        if (value.equals("true")) {
            return true;
        }
        throw new IllegalStateException("-D" + INJECT + " must be true or false, not \"" + value + "\"");
    }
}
