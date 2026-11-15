package io.github.h7kz.proofstone.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a test class as a Proofstone end-to-end test (test-api spec §1). The class also needs at least one
 * {@link Matrix}, and its {@link CellTest} methods run once per cell.
 *
 * <p>The JUnit extension that gives this annotation meaning is in {@code proofstone-core}. It is registered by
 * JUnit's extension auto-detection, so the test project sets
 * {@code junit.jupiter.extensions.autodetection.enabled=true} (the example's {@code junit-platform.properties}).
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Proofstone {

    /**
     * Plugin jar globs relative to the project directory. Each must match exactly one file, else the run fails.
     */
    String[] plugins() default {};

    /**
     * Vanilla data packs, as globs relative to the project directory. Each must match exactly one {@code .zip} file or one
     * directory that holds {@code pack.mcmeta}, else the run fails naming the glob. The pack is copied into the world's
     * {@code datapacks} before the server starts and is never reloaded while a test runs (ADR-0017 section 4). Declaring
     * one implies {@link Capability#DATAPACKS_VANILLA}: a cell without it is SKIPPED with the capability named. A declared
     * pack the server did not load makes the class's tests INVALID, naming the pack.
     */
    String[] datapacks() default {};

    /**
     * Accepts the Minecraft EULA (https://aka.ms/MinecraftEULA) in code. Without it the environment variable
     * {@code PROOFSTONE_ACCEPT_EULA=true} is required, otherwise the tests are skipped with that reason.
     */
    boolean acceptEula() default false;
}
