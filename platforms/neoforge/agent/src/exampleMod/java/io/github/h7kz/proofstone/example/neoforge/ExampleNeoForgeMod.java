package io.github.h7kz.proofstone.example.neoforge;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;

/**
 * The example mod under test for the loader host's real-server tests (T-M8-07). A server-only mod that registers nothing
 * (ADR-0017 section 1). It reads {@code config/proofstone-example.properties}, which the host copies in under
 * {@code /config} with the server user's ownership: {@code fail=false} logs one line, {@code fail=true} throws at
 * construction, which is the failed start the host must catch at once. A missing file is a plain declared mod (T-M8-13: a test that declares it through
 * {@code @Proofstone(mods)} has no config) and loads. The crashing variant, {@code exampleCrashModJar}, is this same code with
 * the marker resource {@code proofstone-example-fail} in the jar, which also makes it throw. Built from this
 * source by {@code exampleModJar}; no downloaded artefact.
 */
@Mod(value = "proofstone_example", dist = Dist.DEDICATED_SERVER)
public final class ExampleNeoForgeMod {

    static final String LOADED_LINE = "[proofstone-example] example mod loaded";
    /** A resource only the crashing variant's jar holds (the agent's {@code exampleCrashModJar}). */
    static final String FAIL_MARKER = "proofstone-example-fail";
    private static final Path CONFIG = Path.of("config", "proofstone-example.properties");

    public ExampleNeoForgeMod() {
        if (ExampleNeoForgeMod.class.getResource("/" + FAIL_MARKER) != null) {
            throw new IllegalStateException("example mod was asked to fail by the " + FAIL_MARKER + " resource in its jar");
        }
        String config = "";
        if (Files.exists(CONFIG)) {
            try {
                config = Files.readString(CONFIG);
            } catch (IOException e) {
                throw new UncheckedIOException("example mod cannot read " + CONFIG.toAbsolutePath(), e);
            }
        }
        if (config.contains("fail=true")) {
            throw new IllegalStateException("example mod was asked to fail by " + CONFIG);
        }
        System.out.println(LOADED_LINE);
    }
}
