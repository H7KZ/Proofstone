package dev.proofstone.agent.scheduler;

import dev.proofstone.agent.protocol.AgentException;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Runs agent work on the thread that owns the data it touches (ADR-0002, docs/platforms/folia.md). Paper runs
 * everything on its main thread; Folia routes each {@link Affinity} to the scheduler of its owner.
 */
public interface Scheduler {

    /**
     * Runs {@code task} on the thread that owns {@code affinity}, and returns at once. If the owner is gone before the
     * task can run (a player who logged out, a region that is not loaded), {@code dropped} gets the error instead
     * and {@code task} never runs, so a caller waits for an answer and never hangs.
     */
    void run(Affinity affinity, Runnable task, Consumer<AgentException> dropped);

    /** Runs {@code task} on every global tick until {@link #close()}. */
    void repeatGlobally(Runnable task);

    /**
     * Per-region tick counts (ticks.json, {@code regions}), keyed by {@code world:chunkX,chunkZ} for each chunk that
     * an operation touched. {@code null} on a platform with no regions, so the field is absent there.
     */
    Map<String, Long> regionTicks();

    /** Cancels the repeating tasks this scheduler started. */
    void close();
}
