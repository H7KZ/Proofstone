package dev.proofstone.agent.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.proofstone.agent.protocol.AgentException;
import org.junit.jupiter.api.Test;

class MainThreadTest {

    @Test
    void returnsWhatTheTaskProduced() {
        assertThat(MainThread.call(Runnable::run, () -> "done", 1000)).isEqualTo("done");
    }

    @Test
    void timesOutInsteadOfWaitingForever() {
        Scheduler neverRuns = task -> { };

        assertThatThrownBy(() -> MainThread.call(neverRuns, () -> "late", 50))
                .isInstanceOfSatisfying(AgentException.class, e -> {
                    assertThat(e.code()).isEqualTo("TIMEOUT");
                    assertThat(e.status()).isEqualTo(504);
                });
    }

    @Test
    void anAgentErrorFromTheMainThreadKeepsItsCode() {
        assertThatThrownBy(() -> MainThread.call(Runnable::run, () -> {
            throw new AgentException(404, "PLAYER_NOT_FOUND", "no player", null);
        }, 1000)).isInstanceOfSatisfying(AgentException.class,
                e -> assertThat(e.code()).isEqualTo("PLAYER_NOT_FOUND"));
    }
}
