package io.github.h7kz.proofstone.core.host;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * A mod fault injected on purpose (T-M8-11), switched on by {@code -Dproofstone.injectModFault}, the way
 * {@code injectTopologyFault} breaks a topology. {@code @Proofstone} has no {@code mods} element yet (the API is unchanged by
 * the card), so a bad test cannot declare a mod; the switch installs the example mod under test instead. Each fault must make
 * the loader cell INVALID with the mod named; a fault that passes is a failure of the meta test. Test-only: nothing in a user
 * suite sets the property.
 */
public enum ModFault {
    /** No fault: the loader cell runs with no mod under test. */
    NONE("false"),
    /** The example mod is installed with the config that makes it throw at init, so the loader gives up at start. */
    CRASH_AT_LOAD("crash-at-load");

    /** The system property that selects a fault. The Gradle build sets it for the bad-test run. */
    public static final String PROPERTY = "proofstone.injectModFault";
    /** The example mod's jar (built by the loader agent's {@code exampleModJar}), a path in a system property. */
    public static final String EXAMPLE_MOD = "proofstone.exampleMod";
    /** The config the example mod reads; the host copies it in under {@code /config} owned by the server user. */
    static final String EXAMPLE_CONFIG = "/config/proofstone-example.properties";

    private final String value;

    ModFault(String value) {
        this.value = value;
    }

    /** The property value that selects this fault. */
    public String value() {
        return value;
    }

    /** {@code null}, empty and {@code false} mean no fault; anything not listed is an error, never a silent clean run. */
    public static ModFault parse(String value) {
        if (value == null || value.isBlank()) {
            return NONE;
        }
        for (ModFault fault : values()) {
            if (fault.value.equals(value)) {
                return fault;
            }
        }
        throw new IllegalArgumentException("-D" + PROPERTY + " must be one of "
                + Arrays.stream(values()).map(ModFault::value).collect(Collectors.joining(", ")) + ", not \"" + value + "\"");
    }

    /** The fault the current run asks for. */
    public static ModFault fromSystemProperty() {
        return parse(System.getProperty(PROPERTY));
    }

    /**
     * The options with this fault applied. A fault with no example mod to install is an error: a run that silently
     * injected nothing would be a clean run, and a clean run passes.
     */
    public LoaderHostOptions apply(LoaderHostOptions options, String exampleMod) {
        if (this == NONE) {
            return options;
        }
        if (exampleMod == null || exampleMod.isBlank()) {
            throw new IllegalStateException("-D" + PROPERTY + "=" + value + " needs -D" + EXAMPLE_MOD + ", the example mod's "
                    + "jar (the loader agent's exampleModJar); without it nothing would be injected");
        }
        Path jar = Path.of(exampleMod).toAbsolutePath();
        return options.withMods(jar.getParent(), List.of(jar.getFileName().toString()))
                .withFiles(Map.of(EXAMPLE_CONFIG, "fail=true\n"));
    }
}
