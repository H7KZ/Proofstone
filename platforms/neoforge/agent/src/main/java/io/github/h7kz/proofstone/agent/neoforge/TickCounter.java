package io.github.h7kz.proofstone.agent.neoforge;

import io.github.h7kz.proofstone.agent.protocol.AgentException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * The global tick counter (ticks.json): the server ticks counted since the agent loaded, from NeoForge's end-of-tick
 * callback. {@code await} completes from that callback, so no thread polls or sleeps (AGENTS.md section 3).
 */
final class TickCounter {

    private long ticks;
    private final List<Waiter> waiters = new ArrayList<>();

    /** Called once per server tick, on the server thread. */
    synchronized void tick() {
        ticks++;
        for (Iterator<Waiter> it = waiters.iterator(); it.hasNext(); ) {
            Waiter waiter = it.next();
            if (ticks >= waiter.target) {
                waiter.done.complete(ticks);
                it.remove();
            }
        }
    }

    synchronized long current() {
        return ticks;
    }

    /** Waits on the calling thread, never the server thread, until {@code n} more ticks have passed. */
    long await(long n, long timeoutMs) {
        Waiter waiter;
        synchronized (this) {
            waiter = new Waiter(ticks + n);
            if (n <= 0) {
                return ticks;
            }
            waiters.add(waiter);
        }
        try {
            return waiter.done.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            synchronized (this) {
                waiters.remove(waiter);
            }
            throw new AgentException(504, "TIMEOUT", "no " + n + " ticks within " + timeoutMs + " ms", null);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AgentException(500, "INTERNAL", "interrupted while waiting for ticks", null);
        } catch (ExecutionException e) {
            throw new AgentException(500, "INTERNAL", String.valueOf(e.getCause()), null);
        }
    }

    private static final class Waiter {
        final long target;
        final CompletableFuture<Long> done = new CompletableFuture<>();

        Waiter(long target) {
            this.target = target;
        }
    }
}
