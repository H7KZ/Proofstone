package io.github.h7kz.proofstone.build.arch.fixtures;

import org.junit.jupiter.api.extension.ExtendWith;

// Violates NO_RETRIES: uses a retry extension outside conformance.flake.
@ExtendWith(RetryExtension.class)
public class RetryExtensionFixture {
}

class RetryExtension implements org.junit.jupiter.api.extension.Extension {
}
