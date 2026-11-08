package dev.proofstone.core.junit;

import dev.proofstone.api.ControlRun;
import dev.proofstone.core.host.LeakDetector;
import dev.proofstone.core.verdict.Attempt;
import dev.proofstone.core.verdict.CellResult;
import dev.proofstone.core.verdict.CellRun;
import dev.proofstone.core.verdict.ConformanceResult;
import dev.proofstone.core.verdict.Verdict;
import dev.proofstone.core.verdict.VerdictEngine;
import java.lang.reflect.Method;
import java.util.ArrayList;
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
     * @param conformance the cell's conformance result, from {@code ConformanceCache}
     * @return the failure that makes the passing test non-green, or {@code null} when its verdict is PASS
     */
    static AssertionError judgePass(Method test, String cell, boolean controlRun, ConformanceResult conformance) {
        String id = test.getDeclaringClass().getSimpleName() + "." + test.getName();
        Optional<ControlRun> expectation = expectation(test, id);
        CellResult result = resolve(id, cell, List.of(Attempt.pass()), controlRun, expectation, conformance, false);
        if (result.verdict() == Verdict.PASS) {
            announceExpectedPass(controlRun, id, cell, expectation);
            return null;
        }
        return new AssertionError(result.verdict() + " " + id + " on " + cell + ": " + result.reason());
    }

    /**
     * Judges all N runs of a test on a cell together (self-tests section 4). A change of verdict across the runs is
     * {@code FLAKY}; a control run where every run passes is {@code VACUOUS}. An all-{@code FAIL} result is not
     * reported here: JUnit already reports each failed run, so a second failure would only repeat it.
     *
     * @param attempts every run, in order, with the failing runs' details
     * @param conformance the cell's conformance result, from {@code ConformanceCache}
     * @return the failure that makes the test non-green, or {@code null} when the runs agree or every run failed
     */
    static AssertionError judgeRepeats(Method test, String cell, boolean controlRun, List<Attempt> attempts,
            ConformanceResult conformance) {
        String id = test.getDeclaringClass().getSimpleName() + "." + test.getName();
        Optional<ControlRun> expectation = expectation(test, id);
        CellResult result = resolve(id, cell, attempts, controlRun, expectation, conformance, false);
        return switch (result.verdict()) {
            case PASS -> {
                announceExpectedPass(controlRun, id, cell, expectation);
                yield null;
            }
            case FAIL -> null;
            default -> new AssertionError(result.verdict() + " " + id + " on " + cell + " over " + attempts.size()
                    + " runs: " + result.reason() + failingDetails(attempts));
        };
    }

    /**
     * The report's verdict for one run of a test that has no flake ledger (T-M4-09). It uses the same rules as the
     * test's own judgement, so the report and the JUnit result cannot disagree.
     *
     * @param name the test's name in the report, for example {@code Class.method [mcpl]}
     * @param attempt the run's own outcome, before the verdict rules
     * @param disagreement whether a differential comparison found a mismatch for this run
     * @param conformance the cell's conformance result, or {@code null} when the cell never got one (its boot failed)
     */
    static CellResult single(String name, Method test, String cell, boolean controlRun, Attempt attempt,
            boolean disagreement, ConformanceResult conformance) {
        return judge(name, test, cell, controlRun, List.of(attempt), disagreement, conformance);
    }

    /**
     * The report's verdict over all N runs of a test on a cell (self-tests section 4). The reason lists the outcome of
     * every run, so a FLAKY report shows which runs passed and which failed.
     */
    static CellResult repeated(String name, Method test, String cell, boolean controlRun, List<Attempt> attempts,
            ConformanceResult conformance) {
        CellResult result = judge(name, test, cell, controlRun, attempts, false, conformance);
        if (result.reason() == null || attempts.size() < 2) {
            return result;
        }
        List<String> outcomes = attempts.stream().map(attempt -> attempt.outcome().name()).toList();
        return new CellResult(result.test(), result.cell(), result.verdict(),
                result.reason() + " (runs: " + String.join(", ", outcomes) + ")", result.skipReason(), result.runs());
    }

    private static CellResult judge(String name, Method test, String cell, boolean controlRun, List<Attempt> attempts,
            boolean disagreement, ConformanceResult conformance) {
        Optional<ControlRun> expectation = expectation(test, name);
        if (conformance == null) {
            // Without a conformance result there is no engine verdict (self-tests section 1). Only a failure can get
            // here: the cell never booted, so the run is a FAIL with its own detail, never a pass.
            if (attempts.stream().anyMatch(attempt -> attempt.outcome() != Verdict.FAIL)) {
                throw new IllegalStateException(name + " passed, but its cell has no conformance result");
            }
            String detail = attempts.get(0).detail();
            return new CellResult(name, cell, Verdict.FAIL, detail, null, attempts.size());
        }
        return resolve(name, cell, attempts, controlRun, expectation, conformance, disagreement);
    }

    private static Optional<ControlRun> expectation(Method test, String id) {
        Optional<ControlRun> expectation = AnnotationSupport.findAnnotation(test, ControlRun.class);
        if (expectation.isPresent() && expectation.get().reason().isBlank()) {
            throw new IllegalStateException(id + ": @ControlRun needs a reason");
        }
        return expectation;
    }

    /**
     * @return the test's INVALID failure when the cell's conformance failed, otherwise {@code null}: a plugin test on
     *     that cell never gets a verdict of its own (self-tests section 1)
     */
    static AssertionError judgeInvalid(Method test, String cell, ConformanceResult conformance) {
        String id = test.getDeclaringClass().getSimpleName() + "." + test.getName();
        CellResult result = resolve(id, cell, List.of(Attempt.pass()), false, Optional.empty(), conformance, false);
        return result.verdict() == Verdict.INVALID
                ? new AssertionError(result.verdict() + " " + id + " on " + cell + ": " + result.reason())
                : null;
    }

    /**
     * The leak detector's judgement (robustness R6). It is a failure, not a verdict of its own: the spec's verdict list
     * has no leak, so a leak reads as FAIL with the message starting {@code LEAK}.
     *
     * @param before the server state before the test, from {@link LeakDetector#snapshot}
     * @param after the server state after the test
     * @return the failure naming what the test left changed, or {@code null} when nothing changed
     */
    static AssertionError judgeLeak(Method test, String cell, List<String> before, List<String> after) {
        List<String> changed = LeakDetector.changed(before, after);
        if (changed.isEmpty()) {
            return null;
        }
        String id = test.getDeclaringClass().getSimpleName() + "." + test.getName();
        return new AssertionError("LEAK " + id + " on " + cell + ": state left behind by the test: "
                + String.join("; ", changed));
    }

    private static CellResult resolve(String id, String cell, List<Attempt> attempts, boolean controlRun,
            Optional<ControlRun> expectation, ConformanceResult conformance, boolean disagreement) {
        // An expected pass is a genuine invariant, not a vacuity signal, so the engine judges it as a non-control run.
        boolean vacuityCheck = controlRun && expectation.isEmpty();
        return VerdictEngine.resolve(new CellRun(id, cell, attempts, vacuityCheck, disagreement), conformance);
    }

    private static void announceExpectedPass(boolean controlRun, String id, String cell,
            Optional<ControlRun> expectation) {
        if (controlRun) {
            System.err.println("PROOFSTONE EXPECTED PASS (control run): " + id + " on " + cell + ": "
                    + expectation.map(ControlRun::reason).orElse("no expectation declared"));
        }
    }

    private static String failingDetails(List<Attempt> attempts) {
        List<String> details = new ArrayList<>();
        for (int run = 0; run < attempts.size(); run++) {
            if (attempts.get(run).outcome() == Verdict.FAIL) {
                details.add("run " + (run + 1) + ": " + attempts.get(run).detail());
            }
        }
        return details.isEmpty() ? "" : "; " + String.join("; ", details);
    }
}
