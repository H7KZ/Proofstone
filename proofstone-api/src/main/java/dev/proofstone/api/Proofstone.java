package dev.proofstone.api;

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
     * Accepts the Minecraft EULA (https://aka.ms/MinecraftEULA) in code. Without it the environment variable
     * {@code PROOFSTONE_ACCEPT_EULA=true} is required, otherwise the tests are skipped with that reason.
     */
    boolean acceptEula() default false;
}
