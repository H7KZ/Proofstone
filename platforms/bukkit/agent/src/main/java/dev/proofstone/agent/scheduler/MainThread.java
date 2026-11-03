package dev.proofstone.agent.scheduler;

import dev.proofstone.agent.protocol.AgentException;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Runs a task on the scheduler's thread and waits for its result. The wait is bounded by the request timeout
 * (spec §1), so the caller gets {@code TIMEOUT} rather than a hang. A timed-out task is cancelled.
 */
public final class MainThread {

    private MainThread() {
    }

    public static <T> T call(Scheduler scheduler, Callable<T> task, long timeoutMs) {
        FutureTask<T> future = new FutureTask<>(task);
        scheduler.run(future);
        try {
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
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
