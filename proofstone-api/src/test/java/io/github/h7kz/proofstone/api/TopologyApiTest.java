package io.github.h7kz.proofstone.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Proxy;
import org.junit.jupiter.api.Test;

/** The additive topology surface (T-M7-10): a custom Client fails loudly on transferTo, and the defaults hold. */
class TopologyApiTest {

    @Test
    void aClientThatDoesNotOverrideTransferToFailsLoudly() {
        Client client = (Client) Proxy.newProxyInstance(Client.class.getClassLoader(), new Class<?>[] {Client.class},
                (proxy, method, args) -> {
                    if (method.isDefault()) {
                        return java.lang.reflect.InvocationHandler.invokeDefault(proxy, method, args);
                    }
                    throw new AssertionError("not called: " + method.getName());
                });

        assertThatThrownBy(() -> client.transferTo("survival")).isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("override transferTo");
    }

    @Topology.Backends
    static class Defaults {
    }

    @Test
    void theDefaultBackendsAreLobbyThenSurvival() {
        assertThat(Defaults.class.getAnnotation(Topology.Backends.class).value()).containsExactly("lobby", "survival");
    }

    @Test
    void theNewConstantsAreAddedAfterTheExistingOnes() {
        assertThat(Platform.values()).startsWith(Platform.PAPER, Platform.FOLIA, Platform.PURPUR, Platform.SPIGOT)
                .endsWith(Platform.VELOCITY, Platform.BUNGEECORD);
        assertThat(Capability.values()).startsWith(Capability.BUKKIT_EVENTS, Capability.CONSOLE,
                Capability.FOLIA_REGIONS, Capability.SIMULATED_BEDROCK).endsWith(Capability.PROXY, Capability.TOPOLOGY);
    }
}
