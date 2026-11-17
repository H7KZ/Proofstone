package io.github.h7kz.proofstone.core.host;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** The injectable Geyser faults of T-M9-11: how they parse. */
class GeyserFaultTest {

    @Test
    void noValueAndFalseMeanNoFault() {
        assertThat(GeyserFault.parse(null)).isEqualTo(GeyserFault.NONE);
        assertThat(GeyserFault.parse("")).isEqualTo(GeyserFault.NONE);
        assertThat(GeyserFault.parse("false")).isEqualTo(GeyserFault.NONE);
    }

    @Test
    void eachFaultParsesFromItsValue() {
        assertThat(GeyserFault.parse("stand-in-absent")).isEqualTo(GeyserFault.STAND_IN_ABSENT);
        assertThat(GeyserFault.parse("key-mismatch")).isEqualTo(GeyserFault.KEY_MISMATCH);
    }

    @Test
    void anUnknownFaultIsAnErrorNamingTheChoicesNeverACleanRun() {
        assertThatThrownBy(() -> GeyserFault.parse("refused-protocol")).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("proofstone.injectGeyserFault").hasMessageContaining("\"refused-protocol\"")
                .hasMessageContaining("stand-in-absent").hasMessageContaining("key-mismatch");
    }
}
