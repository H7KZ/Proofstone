package io.github.h7kz.proofstone.core.junit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.h7kz.proofstone.core.junit.CellConformance.Fault;
import org.junit.jupiter.api.Test;

class CellConformanceTest {

    @Test
    void anUnsetOrFalseSwitchInjectsNothing() {
        assertThat(CellConformance.faultFor(null)).isEqualTo(Fault.NONE);
        assertThat(CellConformance.faultFor("false")).isEqualTo(Fault.NONE);
    }

    @Test
    void trueBreaksTheConsoleCheck() {
        assertThat(CellConformance.faultFor("true")).isEqualTo(Fault.LIST);
    }

    @Test
    void windowBreaksTheWindowScenario() {
        assertThat(CellConformance.faultFor("window")).isEqualTo(Fault.WINDOW);
    }

    @Test
    void anyOtherValueIsAnErrorNeverADefault() {
        assertThatThrownBy(() -> CellConformance.faultFor("maybe"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("maybe");
    }
}
