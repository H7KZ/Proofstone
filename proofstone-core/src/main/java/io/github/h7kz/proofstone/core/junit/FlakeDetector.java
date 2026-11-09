package io.github.h7kz.proofstone.core.junit;

/**
 * The flake detector's switch (self-tests section 4, ADR-0007): {@code -Dproofstone.repeat=N} runs every test N times
 * per cell, and the runs are judged together by {@link VerdictGate#judgeRepeats}. It is the only place a test runs
 * twice on purpose. A failed run is never retried to turn red into green (AGENTS.md section 3).
 */
final class FlakeDetector {

    /** The system property that sets N. The Gradle build forwards it to the test JVM. */
    static final String REPEAT = "proofstone.repeat";
    /** The smallest N that can show a change of verdict (self-tests section 4). */
    static final int MIN_REPEATS = 3;

    private FlakeDetector() {
    }

    /**
     * @return 1 when the detector is off (the property is unset), otherwise N; a value that is not an integer of at
     *     least {@link #MIN_REPEATS} is an error, never a default, so a typo cannot silently turn the check off
     */
    static int repeats() {
        String value = System.getProperty(REPEAT);
        if (value == null) {
            return 1;
        }
        int repeats;
        try {
            repeats = Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalStateException("-D" + REPEAT + " must be an integer of at least " + MIN_REPEATS
                    + ", not \"" + value + "\"", e);
        }
        if (repeats < MIN_REPEATS) {
            throw new IllegalStateException("-D" + REPEAT + " must be at least " + MIN_REPEATS + " (a flake needs "
                    + "several runs to show), not " + repeats);
        }
        return repeats;
    }
}
