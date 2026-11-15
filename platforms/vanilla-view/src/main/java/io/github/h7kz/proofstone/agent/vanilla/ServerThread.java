package io.github.h7kz.proofstone.agent.vanilla;

import io.github.h7kz.proofstone.agent.protocol.AgentException;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Runs endpoint work on the server thread (spec section 1, threading), as the Bukkit agent runs it on the main thread:
 * game state is only safe to touch there. The HTTP thread waits for the reply, bounded by the request's timeout, so the
 * caller gets {@code TIMEOUT} rather than a hang. The executor is the server's own task queue.
 */
public final class ServerThread {

    private final Executor server;

    public ServerThread(Executor server) {
        this.server = server;
    }

    public <T> T call(Callable<T> work, long timeoutMs) {
        CompletableFuture<T> result = new CompletableFuture<>();
        server.execute(() -> {
            try {
                result.complete(work.call());
            } catch (Throwable e) {
                result.completeExceptionally(e);
            }
        });
        try {
            return result.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
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
