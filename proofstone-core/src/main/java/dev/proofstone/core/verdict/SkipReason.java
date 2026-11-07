package dev.proofstone.core.verdict;

/**
 * The allowed reasons for a {@link Verdict#SKIPPED} verdict (test-api spec section 7). Any other reason is not a
 * skip; it is an error. A free-text detail accompanies each reason in the report.
 */
public enum SkipReason {
    /** The cell lacks a capability the test requires, for example the grindstone before 1.14 (C07). */
    CAPABILITY_MISSING,
    DOCKER_UNAVAILABLE,
    EULA_NOT_ACCEPTED,
    /** The run selector does not name this cell's platform or version (test-api spec section 1). */
    CELL_NOT_SELECTED
}
