package dev.proofstone.core.verdict;

/**
 * The verdict of one test on one cell (test-api spec section 7). Only {@link #PASS} and {@link #SKIPPED} are green.
 * A {@code SKIPPED} verdict always carries a {@link SkipReason} from the allowed set, so a skip is never a pass
 * (AGENTS.md section 3).
 */
public enum Verdict {
    PASS,
    FAIL,
    /** The cell's conformance failed, so no plugin verdict exists for it (glossary: Invalid). */
    INVALID,
    /** Passed without the plugin in a control run (glossary: Vacuous test). */
    VACUOUS,
    /** Verdicts differ across repeated runs (glossary: Flake). */
    FLAKY,
    /** Client and reference driver observations differ (glossary: Differential run). */
    DISAGREE,
    SKIPPED;

    /** @return whether this verdict counts as green for the run's exit status */
    public boolean isGreen() {
        return this == PASS || this == SKIPPED;
    }
}
