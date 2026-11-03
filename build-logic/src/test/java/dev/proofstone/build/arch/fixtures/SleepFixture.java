package dev.proofstone.build.arch.fixtures;

import java.util.concurrent.TimeUnit;

// Violates NO_SLEEP twice: Thread.sleep and TimeUnit.sleep.
public class SleepFixture {
    public void waitBadly() throws InterruptedException {
        Thread.sleep(1L);
        TimeUnit.MILLISECONDS.sleep(1L);
    }
}
