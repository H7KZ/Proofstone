package io.github.h7kz.proofstone.driver.bedrock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** The test-only protocol switch of T-M10-05: off by default, and refused unless the Geyser fault injection also asks for it. */
class ProtocolOverrideTest {

    private static final int CODEC = 2225;

    @Test
    void isOffByDefault() {
        assertThat(ProtocolOverride.resolve(null, null, CODEC)).isEqualTo(CODEC);
        assertThat(ProtocolOverride.resolve("", "", CODEC)).isEqualTo(CODEC);
        assertThat(ProtocolOverride.resolve(null, "refused-protocol", CODEC)).isEqualTo(CODEC);
    }

    @Test
    void appliesOnlyWhenTheRefusedProtocolFaultIsAlsoSet() {
        assertThat(ProtocolOverride.resolve("1", "refused-protocol", CODEC)).isEqualTo(1);
    }

    @Test
    void isRefusedOutsideTheFault() {
        assertThatThrownBy(() -> ProtocolOverride.resolve("1", null, CODEC)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("proofstone.bedrock.protocolOverride").hasMessageContaining("proofstone.injectGeyserFault=refused-protocol");
        assertThatThrownBy(() -> ProtocolOverride.resolve("1", "key-mismatch", CODEC)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> ProtocolOverride.resolve("1", "false", CODEC)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void anUnusableNumberIsAnErrorNeverTheCodecsOwn() {
        assertThatThrownBy(() -> ProtocolOverride.resolve("abc", "refused-protocol", CODEC)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("\"abc\"");
        assertThatThrownBy(() -> ProtocolOverride.resolve("0", "refused-protocol", CODEC)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> ProtocolOverride.resolve("2225", "refused-protocol", CODEC)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not a refusal");
    }
}
