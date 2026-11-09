package io.github.h7kz.proofstone.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * A custom {@link Cell} that does not override {@link Cell#capabilities()} must fail loudly, never skip or pass silently
 * (AGENTS.md section 3, fail loudly on unknowns).
 */
class CellCapabilitiesDefaultTest {

    private final Cell minimal = new Cell() {
        @Override
        public String platform() {
            return "paper";
        }

        @Override
        public String version() {
            return "26.3";
        }

        @Override
        public String javaTag() {
            return "java25";
        }

        @Override
        public boolean bridged() {
            return false;
        }
    };

    @Test
    void theDefaultThrowsWithAMessageThatNamesTheFix() {
        assertThatThrownBy(minimal::capabilities)
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessage("this Cell implementation does not declare its capabilities; override capabilities()");
    }

    @Test
    void theOtherMembersStillWorkWithoutAnOverride() {
        assertThat(minimal.platform()).isEqualTo("paper");
        assertThat(minimal.version()).isEqualTo("26.3");
        assertThat(minimal.javaTag()).isEqualTo("java25");
        assertThat(minimal.bridged()).isFalse();
    }
}
