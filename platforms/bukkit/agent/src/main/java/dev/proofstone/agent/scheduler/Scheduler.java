package dev.proofstone.agent.scheduler;

/** Runs agent work on the thread that owns the server state. Folia gets its own implementation in M5. */
public interface Scheduler {

    void run(Runnable task);
}
