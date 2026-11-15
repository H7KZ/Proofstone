package io.github.h7kz.proofstone.agent.vanilla;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.h7kz.proofstone.agent.protocol.AgentException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;

/** The two threading helpers both loader agents share: the work hop to the server thread, and the tick counter. */
class ServerThreadAndTicksTest {

    @Test
    void workRunsOnTheServerThreadAndItsAgentExceptionPassesThrough() {
        ExecutorService server = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "Server thread");
            thread.setDaemon(true);
            return thread;
        });
        try {
            ServerThread thread = new ServerThread(server);
            assertThat(thread.call(() -> Thread.currentThread().getName(), 5_000)).isEqualTo("Server thread");
            assertThatThrownBy(() -> thread.call(() -> {
                throw new AgentException(404, "PLAYER_NOT_FOUND", "no player", null);
            }, 5_000)).isInstanceOfSatisfying(AgentException.class, e -> assertThat(e.code()).isEqualTo("PLAYER_NOT_FOUND"));
        } finally {
            server.shutdownNow();
        }
    }

    @Test
    void aServerThatNeverRunsTheTaskIsATimeoutNotAHang() {
        ServerThread thread = new ServerThread(task -> { });
        assertThatThrownBy(() -> thread.call(() -> "never", 50)).isInstanceOfSatisfying(AgentException.class,
                e -> assertThat(e.code()).isEqualTo("TIMEOUT"));
    }

    @Test
    void awaitCompletesFromTheTickWithoutPolling() {
        TickCounter ticks = new TickCounter();
        CompletableFuture<Long> waiting = CompletableFuture.supplyAsync(() -> ticks.await(2, 10_000));
        // Ticks until the waiter has registered and been reached: the counter, not a sleep, decides when it completes.
        while (!waiting.isDone()) {
            ticks.tick();
            Thread.onSpinWait();
        }
        assertThat(waiting.join()).isGreaterThanOrEqualTo(2);
        assertThat(ticks.current()).isGreaterThanOrEqualTo(2);
        assertThat(ticks.await(0, 1)).isEqualTo(ticks.current());
    }

    @Test
    void awaitTimesOutWhenTheServerStopsTicking() {
        TickCounter ticks = new TickCounter();
        assertThatThrownBy(() -> ticks.await(1, 50)).isInstanceOfSatisfying(AgentException.class,
                e -> assertThat(e.code()).isEqualTo("TIMEOUT"));
    }
}
