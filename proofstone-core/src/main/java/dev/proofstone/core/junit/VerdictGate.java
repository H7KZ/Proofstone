package dev.proofstone.core.junit;

import dev.proofstone.api.ControlRun;
import dev.proofstone.core.verdict.Attempt;
import dev.proofstone.core.verdict.CellResult;
import dev.proofstone.core.verdict.CellRun;
import dev.proofstone.core.verdict.ConformanceResult;
import dev.proofstone.core.verdict.Verdict;
import dev.proofstone.core.verdict.VerdictEngine;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import org.junit.platform.commons.support.AnnotationSupport;

/**
 * Applies {@link VerdictEngine} to the outcome of one test, after it ran (self-tests sections 1 and 2). Only a passing
 * test is judged here: a failing or skipped test already has its verdict from JUnit, and a skip is never a pass.
 *
 * <p>A control run ({@code -Dproofstone.controlRun=true}) installs no plugin jars, so a pass is {@code VACUOUS} and
 * fails the test, unless the test declares {@link ControlRun} with an expected pass.
 */
final class VerdictGate {

    /** The system property that turns on a control run. The Gradle build forwards it to the test JVM. */
    static final String CONTROL_RUN = "proofstone.controlRun";

    private VerdictGate() {
    }

    /**
     * @return whether this is a control run; a value other than {@code true} or {@code false} is an error, never a
     *     default, so a typo cannot silently turn the check off
     */
    static boolean controlRun() {
        String value = System.getProperty(CONTROL_RUN);
        if (value == null || value.equals("false")) {
            return false;
        }
        if (value.equals("true")) {
            return true;
        }
        throw new IllegalStateException("-D" + CONTROL_RUN + " must be true or false, not \"" + value + "\"");
    }

    /**
     * @param test the test method that passed
     * @param cell the cell's display id, for example {@code paper@1.8.8}
     * @param controlRun whether this run is a control run
     * @return the failure that makes the passing test non-green, or {@code null} when its verdict is PASS
     */
    static AssertionError judgePass(Method test, String cell, boolean controlRun) {
        String id = test.getDeclaringClass().getSimpleName() + "." + test.getName();
        Optional<ControlRun> expectation = AnnotationSupport.findAnnotation(test, ControlRun.class);
        if (expectation.isPresent() && expectation.get().reason().isBlank()) {
            throw new IllegalStateException(id + ": @ControlRun needs a reason");
        }
        // An expected pass is a genuine invariant, not a vacuity signal, so the engine judges it as a non-control run.
        boolean vacuityCheck = controlRun && expectation.isEmpty();
        // Conformance is not run by the extension yet: no runner feeds ConformanceCache, so INVALID cannot fire here.
        // Tracked as a gap in the card's PR; the verdict below rests only on the test's own pass.
        CellResult result = VerdictEngine.resolve(
                new CellRun(id, cell, List.of(Attempt.pass()), vacuityCheck, false),
                ConformanceResult.pass(cell));
        if (result.verdict() == Verdict.PASS) {
            if (controlRun) {
                System.err.println("PROOFSTONE EXPECTED PASS (control run): " + id + " on " + cell + ": "
                        + expectation.map(ControlRun::reason).orElse("no expectation declared"));
            }
            return null;
        }
        return new AssertionError(result.verdict() + " " + id + " on " + cell + ": " + result.reason());
    }
}
