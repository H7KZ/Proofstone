package io.github.h7kz.proofstone.core.junit;

import io.github.h7kz.proofstone.api.Agent;
import io.github.h7kz.proofstone.core.conformance.C03GiveRoundTrip;
import io.github.h7kz.proofstone.core.conformance.C04ChestMove;
import io.github.h7kz.proofstone.core.conformance.C05AnvilRenameCost;
import io.github.h7kz.proofstone.core.conformance.C06AnvilCombine;
import io.github.h7kz.proofstone.core.conformance.C07Grindstone;
import io.github.h7kz.proofstone.core.conformance.C08ShiftClickCraft;
import io.github.h7kz.proofstone.core.host.PaperHost;
import io.github.h7kz.proofstone.core.verdict.ConformanceResult;
import java.util.Set;
import java.util.function.Function;

/**
 * The conformance run of one cell (self-tests section 1), run once per cell per run through {@code ConformanceCache}.
 * It has two parts, and a cell fails when either fails:
 *
 * <ol>
 *   <li>the vanilla {@code list} command answers on the console path the harness uses for setup;</li>
 *   <li>the agent and client round trips (T-M4-10b): C03 (give, then read on both oracles) on every cell, and the window
 *       scenarios C04 to C08 on every bridged cell (R1, R3). C07 is skipped with its reason on rows before 1.14.</li>
 * </ol>
 *
 * <p>Scenario failures are a failed result, not an error: the cell is {@code INVALID} for every test. A join or a
 * disconnect that fails is a failed scenario too, so a cell that cannot be checked never passes.
 *
 * <p>The switch {@code -Dproofstone.injectConformanceFault} is the conformance-breaking override, used only by the
 * bad-tests meta run: {@code true} breaks the {@code list} check, {@code window} breaks the window scenario (C04).
 */
final class CellConformance {

    /** The system property that turns on a conformance-breaking override. The Gradle build forwards it to the JVM. */
    static final String INJECT = "proofstone.injectConformanceFault";
    /** Part of what vanilla {@code list} prints on every row from 1.8.8 to 26.x. */
    static final String EXPECTED = "players online";
    static final String FAULT_EXPECTED = "PROOFSTONE-CONFORMANCE-FAULT";
    /** The name of the client the conformance run joins with. Tests use their own names (see CellInvocation). */
    static final String CLIENT = "PSConf";

    /** Which conformance-breaking override is on. */
    enum Fault {
        NONE, LIST, WINDOW
    }

    private CellConformance() {
    }

    /**
     * @param cell the cell's display id, for example {@code paper@26.1.2}
     * @param host the cell's server
     * @param view the cell as the tests see it: its bridged flag and capabilities
     * @param join logs one client in under the given name, through the invocation's own driver
     * @return the cell's result; a failed scenario is a {@code false} result, never a throw
     */
    static ConformanceResult run(String cell, PaperHost host, CellView view,
            Function<String, SessionClient> join) {
        Fault fault = fault();
        String expected = fault == Fault.LIST ? FAULT_EXPECTED : EXPECTED;
        long started = System.nanoTime();
        String reply = host.command("list");
        if (reply == null || reply.isBlank()) {
            return new ConformanceResult(cell, false, "`list` gave a blank reply, so the console path is unverified");
        }
        if (!reply.contains(expected)) {
            return new ConformanceResult(cell, false, "`list` did not print \"" + expected + "\": " + reply.trim());
        }
        ConformanceResult scenarios = scenarios(cell, host, view, join, fault == Fault.WINDOW);
        System.err.println("PROOFSTONE CONFORMANCE " + cell + (view.bridged() ? " (bridged)" : "") + ": "
                + (scenarios.passed() ? "passed" : "failed") + " in " + millis(started) + " ms");
        return scenarios.passed() ? ConformanceResult.pass(cell) : scenarios;
    }

    /**
     * Joins one client and runs C03 on the cell, then C04 to C08 when the cell is bridged. The first failing scenario
     * decides the result, and its name is in the detail. The client is always closed.
     */
    private static ConformanceResult scenarios(String cell, PaperHost host, CellView view,
            Function<String, SessionClient> join, boolean windowFault) {
        Agent agent = new HostAgent(host, Set.of());
        SessionClient client;
        try {
            client = join.apply(CLIENT);
        } catch (RuntimeException | AssertionError e) {
            return failed(cell, "the conformance client could not join", e);
        }
        ConformanceResult result = null;
        String current = "C03";
        try {
            timed(cell, "C03", () -> C03GiveRoundTrip.run(view, agent, client));
            if (view.bridged()) {
                current = "C04";
                timed(cell, "C04", () -> C04ChestMove.run(view, agent, client, windowFault));
                current = "C05";
                timed(cell, "C05", () -> C05AnvilRenameCost.run(view, agent, client));
                current = "C06";
                timed(cell, "C06", () -> C06AnvilCombine.run(view, agent, client));
                current = "C07";
                if (C07Grindstone.hasGrindstone(view.version())) {
                    timed(cell, "C07", () -> C07Grindstone.run(view, agent, client));
                } else {
                    System.err.println("PROOFSTONE CONFORMANCE " + cell + ": C07 skipped, grindstone arrived in 1.14");
                }
                current = "C08";
                timed(cell, "C08", () -> C08ShiftClickCraft.run(view, agent, client));
            }
        } catch (RuntimeException | AssertionError e) {
            result = failed(cell, current + " failed", e);
        }
        try {
            client.close();
        } catch (RuntimeException e) {
            if (result == null) {
                result = failed(cell, "the conformance client could not disconnect", e);
            }
        }
        return result == null ? ConformanceResult.pass(cell) : result;
    }

    /** Runs one scenario and prints how long it took, so the cost per cell is in the log (robustness.md section 4). */
    private static void timed(String cell, String name, Runnable scenario) {
        long started = System.nanoTime();
        scenario.run();
        System.err.println("PROOFSTONE CONFORMANCE " + cell + " " + name + " passed in " + millis(started) + " ms");
    }

    private static ConformanceResult failed(String cell, String what, Throwable e) {
        String message = e.getMessage() == null || e.getMessage().isBlank() ? e.getClass().getName() : e.getMessage();
        return new ConformanceResult(cell, false, what + ": " + message);
    }

    private static long millis(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000L;
    }

    /** @return the override switch; a value other than {@code false}, {@code true} or {@code window} is an error */
    static Fault fault() {
        return faultFor(System.getProperty(INJECT));
    }

    /**
     * @param value the property's value, or {@code null} when it is not set
     * @return the override it names; anything else is an error, never a default
     */
    static Fault faultFor(String value) {
        if (value == null || value.equals("false")) {
            return Fault.NONE;
        }
        if (value.equals("true")) {
            return Fault.LIST;
        }
        if (value.equals("window")) {
            return Fault.WINDOW;
        }
        throw new IllegalStateException("-D" + INJECT + " must be false, true or window, not \"" + value + "\"");
    }
}
