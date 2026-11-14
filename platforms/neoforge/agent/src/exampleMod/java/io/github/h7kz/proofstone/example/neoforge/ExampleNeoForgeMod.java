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
 * construction, which is the failed start the host must catch at once. A missing file fails the boot too. Built from this
 * source by {@code exampleModJar}; no downloaded artefact.
 */
@Mod(value = "proofstone_example", dist = Dist.DEDICATED_SERVER)
public final class ExampleNeoForgeMod {

    static final String LOADED_LINE = "[proofstone-example] example mod loaded";
    private static final Path CONFIG = Path.of("config", "proofstone-example.properties");

    public ExampleNeoForgeMod() {
        String config;
        try {
            config = Files.readString(CONFIG);
        } catch (IOException e) {
            throw new UncheckedIOException("example mod cannot read " + CONFIG.toAbsolutePath(), e);
        }
        if (config.contains("fail=true")) {
            throw new IllegalStateException("example mod was asked to fail by " + CONFIG);
        }
        System.out.println(LOADED_LINE);
    }
}
