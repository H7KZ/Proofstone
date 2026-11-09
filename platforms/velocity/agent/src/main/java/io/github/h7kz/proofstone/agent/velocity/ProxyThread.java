package io.github.h7kz.proofstone.agent.velocity;

import com.velocitypowered.api.proxy.ProxyServer;
import io.github.h7kz.proofstone.agent.protocol.AgentException;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Runs the agent's reads on Velocity's own scheduler and waits for the result, bounded by the request timeout (spec §1),
 * so the caller gets {@code TIMEOUT} rather than a hang. Velocity has no main thread: the scheduler is its worker pool.
 * A task whose caller has given up is skipped when its turn comes, so a late task changes nothing.
 */
final class ProxyThread {

    private final ProxyServer proxy;
    private final Object plugin;

    ProxyThread(ProxyServer proxy, Object plugin) {
        this.proxy = proxy;
        this.plugin = plugin;
    }

    <T> T call(Callable<T> task, long timeoutMs) {
        CompletableFuture<T> done = new CompletableFuture<>();
        proxy.getScheduler().buildTask(plugin, () -> {
            if (done.isDone()) {
                return;
            }
            try {
                done.complete(task.call());
            } catch (Exception | LinkageError e) {
                done.completeExceptionally(e);
            }
        }).schedule();
        return await(done, timeoutMs);
    }

    /** Waits for a future the proxy completes (a command, a ping). */
    static <T> T await(CompletableFuture<T> done, long timeoutMs) {
        try {
            return done.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            done.cancel(false);
            throw new AgentException(504, "TIMEOUT", "no reply within " + timeoutMs + " ms", null);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AgentException(500, "INTERNAL", "interrupted while waiting for the proxy", null);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof AgentException) {
                throw (AgentException) cause;
            }
            throw new AgentException(500, "INTERNAL", String.valueOf(cause), null);
        }
    }
}
