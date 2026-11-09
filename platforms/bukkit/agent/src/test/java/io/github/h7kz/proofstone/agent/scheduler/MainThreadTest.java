package io.github.h7kz.proofstone.agent.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.h7kz.proofstone.agent.protocol.AgentException;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

class MainThreadTest {

    /** Runs every task at once on the calling thread, like a scheduler whose owner is always ready. */
    private static final Scheduler INLINE = new Scheduler() {
        @Override
        public void run(Affinity affinity, Runnable task, Consumer<AgentException> dropped) {
            task.run();
        }

        @Override
        public void repeatGlobally(Runnable task) {
        }

        @Override
        public Map<String, Long> regionTicks() {
            return null;
        }

        @Override
        public void close() {
        }
    };

    @Test
    void returnsWhatTheTaskProduced() {
        assertThat(MainThread.call(INLINE, Affinity.global(), () -> "done", 1000)).isEqualTo("done");
    }

    @Test
    void timesOutInsteadOfWaitingForever() {
        Scheduler neverRuns = new Scheduler() {
            @Override
            public void run(Affinity affinity, Runnable task, Consumer<AgentException> dropped) {
            }

            @Override
            public void repeatGlobally(Runnable task) {
            }

            @Override
            public Map<String, Long> regionTicks() {
                return null;
            }

            @Override
            public void close() {
            }
        };

        assertThatThrownBy(() -> MainThread.call(neverRuns, Affinity.global(), () -> "late", 50))
                .isInstanceOfSatisfying(AgentException.class, e -> {
                    assertThat(e.code()).isEqualTo("TIMEOUT");
                    assertThat(e.status()).isEqualTo(504);
                });
    }

    @Test
    void anAgentErrorFromTheOwningThreadKeepsItsCode() {
        assertThatThrownBy(() -> MainThread.call(INLINE, Affinity.player("Alex"), () -> {
            throw new AgentException(404, "PLAYER_NOT_FOUND", "no player", null);
        }, 1000)).isInstanceOfSatisfying(AgentException.class,
                e -> assertThat(e.code()).isEqualTo("PLAYER_NOT_FOUND"));
    }

    @Test
    void aDroppedTaskFailsTheCallerInsteadOfHanging() {
        Scheduler dropsEverything = new Scheduler() {
            @Override
            public void run(Affinity affinity, Runnable task, Consumer<AgentException> dropped) {
                dropped.accept(new AgentException(404, "PLAYER_NOT_FOUND", "gone", null));
            }

            @Override
            public void repeatGlobally(Runnable task) {
            }

            @Override
            public Map<String, Long> regionTicks() {
                return null;
            }

            @Override
            public void close() {
            }
        };

        assertThatThrownBy(() -> MainThread.call(dropsEverything, Affinity.player("Alex"), () -> "x", 1000))
                .isInstanceOfSatisfying(AgentException.class,
                        e -> assertThat(e.code()).isEqualTo("PLAYER_NOT_FOUND"));
    }

    @Test
    void theAffinityKindsAreDistinct() {
        assertThat(Affinity.global().kind()).isEqualTo(Affinity.Kind.GLOBAL);
        assertThat(Affinity.player("Alex").player()).isEqualTo("Alex");
        assertThat(Affinity.block("world", 1, 64, -2).world()).isEqualTo("world");
        assertThat(Affinity.block("world", 1, 64, -2).kind()).isEqualTo(Affinity.Kind.BLOCK);
    }
}
