package io.github.h7kz.proofstone.conformance.flake;

import org.junit.jupiter.api.RepeatedTest;

// Passes NO_RETRIES: the flake detector is the one place allowed to repeat a test.
public class FlakeFixture {
    @RepeatedTest(3)
    void repeated() {
    }
}
