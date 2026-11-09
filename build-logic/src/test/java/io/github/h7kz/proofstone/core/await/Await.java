package io.github.h7kz.proofstone.core.await;

// Passes NO_SLEEP: this is the only place allowed to call Thread.sleep (AGENTS.md §3).
public final class Await {
    private Await() {
    }

    public static void pause(long millis) throws InterruptedException {
        Thread.sleep(millis);
    }
}
