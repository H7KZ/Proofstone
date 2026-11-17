package io.github.h7kz.proofstone.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** The additive Bedrock surface (ADR-0018 section 9, test-api spec section 14). */
class BedrockApiTest {

    @Matrix(platform = Platform.GEYSER, versions = {"latest"})
    static class Declared {
    }

    @Test
    void geyserIsAMatrixPlatformAndBedrockClientIsACapability() {
        assertThat(Declared.class.getAnnotation(Matrix.class).platform()).isEqualTo(Platform.GEYSER);
        assertThat(Capability.valueOf("BEDROCK_CLIENT")).isNotNull();
        // The simulation keeps its own name and meaning (ADR-0018 section 5).
        assertThat(Capability.SIMULATED_BEDROCK).isNotEqualTo(Capability.BEDROCK_CLIENT);
    }

    @Test
    void anImplementationThatDoesNotOverrideBedrockFailsLoudlyAndNamesTheCapability() {
        Clients minimal = name -> {
            throw new AssertionError("create must not be called");
        };
        assertThatThrownBy(() -> minimal.bedrock("PsBed1")).isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("BEDROCK_CLIENT");
    }
}
