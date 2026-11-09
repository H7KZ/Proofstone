package io.github.h7kz.proofstone.core.verdict;

/**
 * The outcome of the conformance suite on one cell (self-tests section 1). A failed cell makes every plugin test on
 * that cell {@link Verdict#INVALID}.
 *
 * @param cell the cell's display id
 * @param passed whether every conformance scenario passed
 * @param detail why it failed; required when {@code passed} is false
 */
public record ConformanceResult(String cell, boolean passed, String detail) {

    public ConformanceResult {
        if (cell == null || cell.isBlank()) {
            throw new IllegalArgumentException("cell must not be blank");
        }
        if (!passed && (detail == null || detail.isBlank())) {
            throw new IllegalArgumentException("a failed conformance result needs a detail: " + cell);
        }
    }

    public static ConformanceResult pass(String cell) {
        return new ConformanceResult(cell, true, null);
    }
}
