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
     * Mod jar globs relative to the project directory, for a loader cell (Fabric, NeoForge). Each must match exactly one
     * file, else the test is INVALID naming the glob. The jar is copied to the server's {@code /mods} before it starts
     * (ADR-0017 section 4). Declaring one implies {@link Capability#MODS}: a cell without it (every Bukkit-family cell) is
     * SKIPPED with the capability named. A declared mod the agent does not list after boot, or one that crashes the loader at
     * load, makes the class's tests INVALID naming the mod. A mod with the id {@code fabric-api} is refused: the Fabric cell
     * provides the pinned Fabric API. A control run installs no declared mod.
     */
    String[] mods() default {};

    /**
     * Accepts the Minecraft EULA (https://aka.ms/MinecraftEULA) in code. Without it the environment variable
     * {@code PROOFSTONE_ACCEPT_EULA=true} is required, otherwise the tests are skipped with that reason.
     */
    boolean acceptEula() default false;
}
