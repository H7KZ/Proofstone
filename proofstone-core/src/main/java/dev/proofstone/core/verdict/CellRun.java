package dev.proofstone.core.verdict;

import java.util.List;
import java.util.Objects;

/**
 * Everything {@link VerdictEngine} needs about one test on one cell.
 *
 * @param test the test's name, unique within the cell
 * @param cell the cell's display id, for example {@code paper@1.8.8}
 * @param attempts the runs in order: one normally, N in the flake detector (ADR-0007)
 * @param controlRun whether the run had its plugin jars removed (self-tests section 2)
 * @param disagreement whether the differential comparator found a mismatch (self-tests section 3)
 */
public record CellRun(String test, String cell, List<Attempt> attempts, boolean controlRun, boolean disagreement) {

    public CellRun {
        requireNonBlank(test, "test");
        requireNonBlank(cell, "cell");
        attempts = List.copyOf(Objects.requireNonNull(attempts, "attempts"));
        // No runs is an error, never a pass: a test that did not run has no verdict to give.
        if (attempts.isEmpty()) {
            throw new IllegalArgumentException(test + " on " + cell + " has no runs");
        }
    }

    private static void requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
