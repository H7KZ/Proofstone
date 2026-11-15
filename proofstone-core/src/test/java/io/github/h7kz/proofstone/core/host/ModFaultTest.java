package io.github.h7kz.proofstone.core.host;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** The mod fault switch (T-M8-11), without Docker. */
class ModFaultTest {

    private static final LoaderHostOptions CLEAN = LoaderHostOptions.of(Path.of("agent.jar"), true, Path.of("cache"));

    @Test
    void noValueAndFalseMeanNoFault() {
        assertThat(ModFault.parse(null)).isEqualTo(ModFault.NONE);
        assertThat(ModFault.parse("")).isEqualTo(ModFault.NONE);
        assertThat(ModFault.parse("false")).isEqualTo(ModFault.NONE);
    }

    @Test
    void anUnknownValueIsAnErrorNotACleanRun() {
        assertThatThrownBy(() -> ModFault.parse("crash")).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("crash-at-load").hasMessageContaining("not \"crash\"");
    }

    @Test
    void crashAtLoadInstallsTheExampleModWithTheConfigThatMakesItThrow() {
        LoaderHostOptions options = ModFault.CRASH_AT_LOAD.apply(CLEAN, "build/example-mod/proofstone-example-mod.jar");

        assertThat(options.modGlobs()).containsExactly("proofstone-example-mod.jar");
        assertThat(options.files()).containsEntry("/config/proofstone-example.properties", "fail=true\n");
        assertThat(options.artefactBase().toString()).endsWith("example-mod");
    }

    @Test
    void noFaultLeavesTheOptionsAsTheyWere() {
        assertThat(ModFault.NONE.apply(CLEAN, null)).isSameAs(CLEAN);
    }

    @Test
    void aFaultWithoutTheExampleModIsAnErrorNotAnInjectionOfNothing() {
        assertThatThrownBy(() -> ModFault.CRASH_AT_LOAD.apply(CLEAN, null)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("proofstone.exampleMod");
    }
}
