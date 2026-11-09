package io.github.h7kz.proofstone.agent.scheduler;

import io.github.h7kz.proofstone.agent.protocol.AgentException;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Runs a task on the thread that owns its affinity and waits for the result through a {@link CompletableFuture}.
 * The wait is bounded by the request timeout (spec §1), so the caller gets {@code TIMEOUT} rather than a hang. A task
 * whose wait has already timed out is skipped when its turn comes, so a late task does not change state the caller
 * gave up on.
 */
public final class MainThread {

    private MainThread() {
    }

    public static <T> T call(Scheduler scheduler, Affinity affinity, Callable<T> task, long timeoutMs) {
        CompletableFuture<T> done = new CompletableFuture<>();
        scheduler.run(affinity, () -> {
            if (done.isDone()) {
                return;
            }
            try {
                done.complete(task.call());
            } catch (Throwable failure) {
                done.completeExceptionally(failure);
            }
        }, done::completeExceptionally);
        return await(done, timeoutMs);
    }

    /**
     * Waits for a result that the server thread completes, bounded by the timeout. Used for work that finishes after
     * its task returns, such as a teleport on Folia.
     */
    public static <T> T await(CompletableFuture<T> done, long timeoutMs) {
        try {
            return done.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            done.cancel(false);
            throw new AgentException(504, "TIMEOUT", "no reply within " + timeoutMs + " ms", null);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AgentException(500, "INTERNAL", "interrupted while waiting for the server thread", null);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof AgentException) {
                throw (AgentException) cause;
            }
            throw new AgentException(500, "INTERNAL", String.valueOf(cause), null);
        }
    }
}
