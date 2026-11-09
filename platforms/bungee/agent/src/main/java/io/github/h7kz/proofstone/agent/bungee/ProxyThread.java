package io.github.h7kz.proofstone.agent.bungee;

import io.github.h7kz.proofstone.agent.protocol.AgentException;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import net.md_5.bungee.api.plugin.Plugin;

/**
 * Runs the agent's reads on BungeeCord's own scheduler and waits for the result, bounded by the request timeout (spec §1),
 * so the caller gets {@code TIMEOUT} rather than a hang. BungeeCord has no main thread: {@code runAsync} hands the task to
 * the plugin's executor service, its worker pool. A task whose caller has given up is skipped when its turn comes, so a
 * late task changes nothing.
 */
final class ProxyThread {

    private final Plugin plugin;

    ProxyThread(Plugin plugin) {
        this.plugin = plugin;
    }

    <T> T call(Callable<T> task, long timeoutMs) {
        CompletableFuture<T> done = new CompletableFuture<>();
        plugin.getProxy().getScheduler().runAsync(plugin, () -> {
            if (done.isDone()) {
                return;
            }
            try {
                done.complete(task.call());
            } catch (Exception | LinkageError e) {
                done.completeExceptionally(e);
            }
        });
        return await(done, timeoutMs);
    }

    /** Waits for a future the proxy completes (a ping). */
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
