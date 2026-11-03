package dev.proofstone.build.arch.fixtures;

import org.junit.jupiter.api.RepeatedTest;

// Violates NO_RETRIES: repeats a test outside conformance.flake.
public class RepeatedTestFixture {
    @RepeatedTest(3)
    void repeated() {
    }
}
