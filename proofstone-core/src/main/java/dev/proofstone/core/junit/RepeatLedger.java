package dev.proofstone.core.junit;

import dev.proofstone.core.verdict.Attempt;
import java.util.ArrayList;
import java.util.List;

/**
 * The runs of one test on one cell, collected across its repeats until the last one (self-tests section 4). JUnit
 * runs the repeats one after another, so the ledger needs no locking. A repeat that arrives out of order means a run
 * was lost, and that is an error, never a verdict on partial evidence.
 */
final class RepeatLedger {

    private final int repeats;
    private final List<Attempt> attempts = new ArrayList<>();

    RepeatLedger(int repeats) {
        this.repeats = repeats;
    }

    /**
     * @param repetition this run's number, from 1 to N
     * @param attempt the run's own outcome
     * @return every run in order once the last repeat is recorded, otherwise {@code null}
     */
    List<Attempt> record(int repetition, Attempt attempt) {
        if (repetition != attempts.size() + 1) {
            throw new IllegalStateException("repeat " + repetition + " arrived after " + attempts.size()
                    + " runs: a run was lost, so no verdict is given");
        }
        attempts.add(attempt);
        return attempts.size() == repeats ? List.copyOf(attempts) : null;
    }
}
