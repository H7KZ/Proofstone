package io.github.h7kz.proofstone.core.host;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.h7kz.proofstone.api.Agent;
import io.github.h7kz.proofstone.api.Capability;
import io.github.h7kz.proofstone.api.Cell;
import io.github.h7kz.proofstone.api.CellTest;
import io.github.h7kz.proofstone.api.Client;
import io.github.h7kz.proofstone.api.DualAssert;
import io.github.h7kz.proofstone.api.Item;
import io.github.h7kz.proofstone.api.Topology;

/**
 * T-M7-10 on a real topology, through the public API only: {@code Topology.server}, {@code Topology.proxy},
 * {@code servers()} and {@code Client.transferTo}, run by the JUnit extension as a plugin author would. One subclass per
 * proxy, each with its own {@code @Matrix} (M7 flake rule: one proxy per invocation).
 *
 * <p>With {@code -Dproofstone.forceFail=true} the first test fails on purpose after its checks, so the run writes a
 * topology repro bundle (a log per container, no secret) for the acceptance evidence; without it the test is green.
 */
abstract class TopologyApiContract {

    @CellTest
    void theTopologyNamesItsBackendsAndEachAgent(Cell cell, Agent agent, Topology topology) {
        assertThat(topology.servers()).containsExactly("lobby", "survival");
        assertThat(cell.capabilities()).contains(Capability.TOPOLOGY, Capability.PROXY, Capability.CONSOLE)
                .doesNotContain(Capability.SIMULATED_BEDROCK);
        assertThat(cell.platform()).isEqualTo("paper");
        assertThat(topology.proxy().capabilities()).contains("proxy");
        assertThat(topology.server("survival").capabilities()).contains("events", "command");
        // The injected Agent is the default backend's (ADR-0016 section 6).
        assertThat(agent.capabilities()).isEqualTo(topology.server("lobby").capabilities());
        assertThatThrownBy(() -> topology.server("creative")).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("[lobby, survival]");
        assertThatThrownBy(() -> topology.proxy().player("Nobody")).isInstanceOf(UnsupportedOperationException.class);
        assertThat(Boolean.getBoolean("proofstone.forceFail")).as("forced failure: -Dproofstone.forceFail=true")
                .isFalse();
    }

    @CellTest
    void aClientSwitchesToSurvivalAndBackAndEachBackendIsTheOracleWhileItIsThere(Topology topology, Client client) {
        // The client joined lobby: its agent lists it, and an item given there is seen by both oracles.
        topology.server("lobby").player(client).give(Item.of("minecraft:apple").amount(3));
        DualAssert.item(topology.server("lobby").player(client), client).inInventory(i -> i.is("apple")).amount(3);

        assertThatThrownBy(() -> client.transferTo("lobby")).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already on lobby");
        assertThatThrownBy(() -> client.transferTo("creative")).isInstanceOf(IllegalArgumentException.class);

        client.transferTo("survival");
        topology.server("survival").player(client).give(Item.of("minecraft:stick").amount(2));
        DualAssert.item(topology.server("survival").player(client), client).inInventory(i -> i.is("stick")).amount(2);
        assertThat(client.inventory()).as("nothing of lobby's inventory on survival").noneMatch(i -> i.is("apple"));

        client.transferTo("lobby");
        DualAssert.item(topology.server("lobby").player(client), client).inInventory(i -> i.is("apple")).amount(3);
        assertThat(client.inventory()).as("nothing of survival's inventory back on lobby").noneMatch(i -> i.is("stick"));
    }
}
