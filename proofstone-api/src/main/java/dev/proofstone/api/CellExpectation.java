package dev.proofstone.api;

/**
 * A per-cell expectation, checked after the test body on each cell the test runs on (test-api spec section 9, robustness
 * R7). A wrong version mapping, such as the wrong NMS module on one Minecraft version, is caught here: the check reads the
 * server through the agent and fails the test when the cell does not match what the suite expects.
 *
 * <p>The check runs only when the test body and the log check passed, and it reads after the test's actions, so it must
 * observe and never change anything. It fails by throwing; any exception is the expectation's failure, reported as
 * {@code FAIL} with its message.
 */
@FunctionalInterface
public interface CellExpectation {

    /** Checks the cell. Throw an {@link AssertionError} with the reason when the cell does not meet the expectation. */
    void check(Cell cell, Agent agent);
}
