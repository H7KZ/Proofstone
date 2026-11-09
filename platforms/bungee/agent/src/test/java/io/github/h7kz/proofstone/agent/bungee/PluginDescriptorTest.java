package io.github.h7kz.proofstone.agent.bungee;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.api.plugin.PluginDescription;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/**
 * BungeeCord finds the plugin through {@code bungee.yml} (read as a {@link PluginDescription}, the way BungeeCord reads it);
 * a wrong entry here is a plugin that silently never loads.
 */
class PluginDescriptorTest {

    private static PluginDescription descriptor() throws IOException {
        try (InputStream in = PluginDescriptorTest.class.getResourceAsStream("/bungee.yml")) {
            assertThat(in).as("bungee.yml on the classpath").isNotNull();
            return new Yaml().loadAs(in, PluginDescription.class);
        }
    }

    @Test
    void theMainClassIsThePluginAndItsVersionMatches() throws Exception {
        PluginDescription description = descriptor();
        Class<?> main = Class.forName(description.getMain());
        assertThat(main).isEqualTo(ProofstoneAgentPlugin.class);
        assertThat(Plugin.class).isAssignableFrom(main);
        assertThat(description.getVersion()).isEqualTo(ProofstoneAgentPlugin.VERSION);
    }

    @Test
    void theNameIsOneBungeeCordAcceptsAndTheAgentDependsOnNothing() throws IOException {
        PluginDescription description = descriptor();
        assertThat(description.getName()).matches("[A-Za-z][A-Za-z0-9_-]*");
        assertThat(description.getDepends()).isEmpty();
        assertThat(description.getSoftDepends()).isEmpty();
        assertThat(description.getLibraries()).as("no runtime downloads (AGENTS.md section 3)").isEmpty();
    }

    @Test
    void thePluginHasTheNoArgumentConstructorBungeeCordUses() throws NoSuchMethodException {
        assertThat(ProofstoneAgentPlugin.class.getConstructor()).isNotNull();
    }
}
