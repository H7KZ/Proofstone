package io.github.h7kz.proofstone.core.junit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ParameterResolutionException;

/**
 * Which agent-jar property a cell needs (T-M8-14): a Bukkit-family cell reads {@code proofstone.agentJar}, a loader cell only its
 * own {@code proofstone.<loader>.agentJar}. A loader run must not have to set the Bukkit agent's jar it never loads.
 */
class AgentJarPropertyTest {

    private String savedBukkit;
    private String savedFabric;

    @BeforeEach
    void save() {
        savedBukkit = System.getProperty("proofstone.agentJar");
        savedFabric = System.getProperty("proofstone.fabric.agentJar");
    }

    @AfterEach
    void restore() {
        set("proofstone.agentJar", savedBukkit);
        set("proofstone.fabric.agentJar", savedFabric);
    }

    private static void set(String key, String value) {
        if (value == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, value);
        }
    }

    @Test
    void aLoaderCellNeedsOnlyItsOwnAgentJar() {
        System.clearProperty("proofstone.agentJar");
        System.setProperty("proofstone.fabric.agentJar", "/jars/fabric-agent.jar");

        assertThat(CellInvocation.agentJar("fabric")).isEqualTo("/jars/fabric-agent.jar");
    }

    @Test
    void aLoaderCellWithoutItsOwnJarFailsNamingThatProperty() {
        System.setProperty("proofstone.agentJar", "/jars/bukkit-agent.jar");
        System.clearProperty("proofstone.fabric.agentJar");

        assertThatThrownBy(() -> CellInvocation.agentJar("fabric"))
                .isInstanceOf(ParameterResolutionException.class)
                .hasMessageContaining("proofstone.fabric.agentJar");
    }

    @Test
    void aBukkitFamilyCellWithoutItsJarStillFailsLoudlyNamingTheProperty() {
        System.clearProperty("proofstone.agentJar");
        System.setProperty("proofstone.fabric.agentJar", "/jars/fabric-agent.jar");

        assertThatThrownBy(() -> CellInvocation.agentJar(null))
                .isInstanceOf(ParameterResolutionException.class)
                .hasMessageContaining("proofstone.agentJar");
    }

    @Test
    void aBukkitFamilyCellReadsTheBukkitJar() {
        System.setProperty("proofstone.agentJar", "/jars/bukkit-agent.jar");

        assertThat(CellInvocation.agentJar(null)).isEqualTo("/jars/bukkit-agent.jar");
    }
}
