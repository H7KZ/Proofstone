package io.github.h7kz.proofstone.agent.bukkit;

import io.github.h7kz.proofstone.agent.protocol.AgentException;
import io.github.h7kz.proofstone.agent.scheduler.Scheduler;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Counts global server ticks with a one-tick repeating task (spec §2). {@link #await(long, long)} waits on the HTTP
 * thread for a future that the tick task completes, so the main thread never blocks and nothing sleeps. The task is
 * started through the scheduler, so on Folia it runs on the global region, not the Bukkit main-thread scheduler.
 */
final class TickCounter {

    private final List<Waiter> waiters = new ArrayList<>();
    private long global;

    /** Starts the repeating task. Call once, from the plugin's enable. */
    static TickCounter start(Scheduler scheduler) {
        TickCounter counter = new TickCounter();
        scheduler.repeatGlobally(counter::tick);
        return counter;
    }

    synchronized long current() {
        return global;
    }

    /** Waits until {@code n} more ticks have passed. Throws TIMEOUT when the server does not get that far in time. */
    long await(long n, long timeoutMs) {
        CompletableFuture<Long> done = new CompletableFuture<>();
        synchronized (this) {
            waiters.add(new Waiter(global + n, done));
        }
        try {
            return done.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            done.cancel(false);
            throw new AgentException(504, "TIMEOUT", "no reply within " + timeoutMs + " ms", null);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AgentException(500, "INTERNAL", "interrupted while waiting for ticks", null);
        } catch (ExecutionException e) {
            throw new AgentException(500, "INTERNAL", String.valueOf(e.getCause()), null);
        }
    }

    private synchronized void tick() {
        global++;
        Iterator<Waiter> pending = waiters.iterator();
        while (pending.hasNext()) {
            Waiter waiter = pending.next();
            if (waiter.done.isDone()) {
                pending.remove();
            } else if (global >= waiter.target) {
                waiter.done.complete(global);
                pending.remove();
            }
        }
    }

    private static final class Waiter {

        private final long target;
        private final CompletableFuture<Long> done;

        Waiter(long target, CompletableFuture<Long> done) {
            this.target = target;
            this.done = done;
        }
    }
}
