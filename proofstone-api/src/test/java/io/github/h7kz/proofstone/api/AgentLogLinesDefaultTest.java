package io.github.h7kz.proofstone.api;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** An {@link Agent} that cannot read the log must say so, never return an empty list a test would read as "no errors". */
class AgentLogLinesDefaultTest {

    private final Agent minimal = new Agent() {
        @Override
        public String command(String command) {
            return "";
        }

        @Override
        public PlayerHandle player(String name) {
            throw new UnsupportedOperationException();
        }

        @Override
        public PluginHandle plugin(String name) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Events events() {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<String> capabilities() {
            return List.of();
        }
    };

    @Test
    void logLinesFailsLoudlyOnAnAgentThatDoesNotOverrideIt() {
        assertThatThrownBy(minimal::logLines).isInstanceOf(UnsupportedOperationException.class)
                .hasMessage("this agent cannot read the server log");
    }

    @Test
    void awaitingALogLineFailsLoudlyOnAnAgentThatDoesNotOverrideIt() {
        assertThatThrownBy(() -> minimal.logLines(Pattern.compile("x"), Duration.ofSeconds(1)))
                .isInstanceOf(UnsupportedOperationException.class).hasMessage("this agent cannot read the server log");
    }
}
