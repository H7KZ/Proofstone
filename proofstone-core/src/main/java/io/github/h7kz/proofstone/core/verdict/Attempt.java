package io.github.h7kz.proofstone.core.verdict;

import java.util.Objects;

/**
 * One run of one test on one cell. It can only be {@link Verdict#PASS}, {@link Verdict#FAIL} or
 * {@link Verdict#SKIPPED}: the other verdicts need several runs or other evidence, and {@link VerdictEngine}
 * derives them.
 *
 * @param outcome the run's own result
 * @param skip the skip reason, set exactly when {@code outcome} is SKIPPED
 * @param detail the failure or skip message; required for FAIL and SKIPPED, optional for PASS
 */
public record Attempt(Verdict outcome, SkipReason skip, String detail) {

    public Attempt {
        Objects.requireNonNull(outcome, "outcome");
        switch (outcome) {
            case PASS -> {
                if (skip != null) {
                    throw new IllegalArgumentException("a PASS run has no skip reason");
                }
            }
            case FAIL -> {
                requireDetail(detail, "FAIL");
                if (skip != null) {
                    throw new IllegalArgumentException("a FAIL run has no skip reason");
                }
            }
            case SKIPPED -> {
                Objects.requireNonNull(skip, "a SKIPPED run needs a SkipReason: a skip is never a pass");
                requireDetail(detail, "SKIPPED");
            }
            default -> throw new IllegalArgumentException("a run is PASS, FAIL or SKIPPED, not " + outcome);
        }
    }

    public static Attempt pass() {
        return new Attempt(Verdict.PASS, null, null);
    }

    public static Attempt fail(String detail) {
        return new Attempt(Verdict.FAIL, null, detail);
    }

    public static Attempt skipped(SkipReason reason, String detail) {
        return new Attempt(Verdict.SKIPPED, reason, detail);
    }

    private static void requireDetail(String detail, String outcome) {
        if (detail == null || detail.isBlank()) {
            throw new IllegalArgumentException(outcome + " run needs a detail message");
        }
    }
}
