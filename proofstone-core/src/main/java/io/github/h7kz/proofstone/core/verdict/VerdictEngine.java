package io.github.h7kz.proofstone.core.verdict;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Turns the runs of one test on one cell into one {@link Verdict} (test-api spec section 7, self-tests sections 1 to
 * 4). The rules are pure, so every verdict has a unit test without Docker.
 *
 * <p>When several rules apply, the first one wins, in this order: {@code INVALID} (the cell's conformance failed),
 * {@code DISAGREE} (the drivers differ), {@code FLAKY} (the runs differ), {@code VACUOUS} (a control-run pass),
 * then the run's own {@code FAIL}, {@code SKIPPED} or {@code PASS}. A flake is a bug, so it outranks the rest of
 * the run's result.
 */
public final class VerdictEngine {

    private VerdictEngine() {
    }

    /**
     * @param run the runs of one test on one cell
     * @param conformance the conformance result of that cell, from {@link ConformanceCache}
     * @return the resolved result, with a reason for every verdict except PASS
     * @throws IllegalArgumentException when the conformance result is for another cell
     */
    public static CellResult resolve(CellRun run, ConformanceResult conformance) {
        Objects.requireNonNull(run, "run");
        // Every cell needs a conformance result before its verdicts. A missing one is an error, never a pass.
        Objects.requireNonNull(conformance, "conformance result missing for " + run.cell());
        if (!conformance.cell().equals(run.cell())) {
            throw new IllegalArgumentException("conformance for " + conformance.cell() + " applied to a run on "
                    + run.cell());
        }
        List<Attempt> attempts = run.attempts();
        int runs = attempts.size();
        if (!conformance.passed()) {
            return result(run, Verdict.INVALID, "cell conformance failed: " + conformance.detail(), null, runs);
        }
        if (run.disagreement()) {
            return result(run, Verdict.DISAGREE, "client and reference driver observations differ", null, runs);
        }
        // A skip and a pass across repeats are a change in verdict too, so the signature includes the skip reason.
        Set<String> kinds = new LinkedHashSet<>();
        for (Attempt attempt : attempts) {
            kinds.add(attempt.skip() == null ? attempt.outcome().name()
                    : attempt.outcome() + "(" + attempt.skip() + ")");
        }
        if (kinds.size() > 1) {
            return result(run, Verdict.FLAKY, "verdicts differ across " + runs + " runs: "
                    + String.join(", ", kinds), null, runs);
        }
        Attempt first = attempts.get(0);
        return switch (first.outcome()) {
            case PASS -> run.controlRun()
                    ? result(run, Verdict.VACUOUS, "passes without the plugin (control run): the test does not prove "
                            + "the plugin works", null, runs)
                    : result(run, Verdict.PASS, null, null, runs);
            case FAIL -> result(run, Verdict.FAIL, first.detail(), null, runs);
            case SKIPPED -> result(run, Verdict.SKIPPED, first.detail(), first.skip(), runs);
            default -> throw new IllegalStateException("unreachable: an attempt is PASS, FAIL or SKIPPED");
        };
    }

    private static CellResult result(CellRun run, Verdict verdict, String reason, SkipReason skip, int runs) {
        return new CellResult(run.test(), run.cell(), verdict, reason, skip, runs);
    }
}
