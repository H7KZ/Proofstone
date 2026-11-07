package dev.proofstone.core.verdict;

/**
 * The verdict of one test on one cell, as written to the report.
 *
 * @param test the test's name
 * @param cell the cell's display id
 * @param verdict the resolved verdict
 * @param reason a human-readable explanation; null only for {@link Verdict#PASS}
 * @param skipReason set exactly when {@code verdict} is SKIPPED
 * @param runs how many runs the verdict was resolved from
 */
public record CellResult(String test, String cell, Verdict verdict, String reason, SkipReason skipReason, int runs) {

    public CellResult {
        if (test == null || test.isBlank() || cell == null || cell.isBlank()) {
            throw new IllegalArgumentException("test and cell must not be blank");
        }
        if (verdict == null) {
            throw new IllegalArgumentException("verdict must be set");
        }
        if (verdict == Verdict.SKIPPED && skipReason == null) {
            throw new IllegalArgumentException(test + " on " + cell + " is SKIPPED without a reason");
        }
        if (verdict != Verdict.SKIPPED && skipReason != null) {
            throw new IllegalArgumentException(test + " on " + cell + " has a skip reason but is " + verdict);
        }
        if (verdict != Verdict.PASS && (reason == null || reason.isBlank())) {
            throw new IllegalArgumentException(test + " on " + cell + " is " + verdict + " without a reason");
        }
        if (runs < 1) {
            throw new IllegalArgumentException("runs must be at least 1");
        }
    }

    /** @return whether this result counts as green for the run's exit status */
    public boolean green() {
        return verdict.isGreen();
    }
}
