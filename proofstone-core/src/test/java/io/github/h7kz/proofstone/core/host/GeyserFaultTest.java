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
        assertThat(GeyserFault.parse("refused-protocol")).isEqualTo(GeyserFault.REFUSED_PROTOCOL);
    }

    @Test
    void anUnknownFaultIsAnErrorNamingTheChoicesNeverACleanRun() {
        assertThatThrownBy(() -> GeyserFault.parse("no-such-fault")).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("proofstone.injectGeyserFault").hasMessageContaining("\"no-such-fault\"")
                .hasMessageContaining("stand-in-absent").hasMessageContaining("key-mismatch").hasMessageContaining("refused-protocol");
    }

    @Test
    void onlyTheRefusedProtocolFaultSetsTheDriverHostSwitch() {
        for (GeyserFault fault : GeyserFault.values()) {
            if (fault != GeyserFault.REFUSED_PROTOCOL) {
                assertThat(fault.driverHostOptions()).as(fault.name()).isEmpty();
            }
        }
        assertThat(GeyserFault.REFUSED_PROTOCOL.driverHostOptions())
                .isEqualTo(" -Dproofstone.injectGeyserFault=refused-protocol -Dproofstone.bedrock.protocolOverride="
                        + GeyserFault.REFUSED_PROTOCOL_NUMBER);
        assertThat(GeyserFault.REFUSED_PROTOCOL_NUMBER).isNotEqualTo(2225);
    }
}
