package io.github.h7kz.proofstone.conformance;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.h7kz.proofstone.api.Capability;
import io.github.h7kz.proofstone.api.Cell;
import io.github.h7kz.proofstone.api.CellTest;
import io.github.h7kz.proofstone.api.Client;
import io.github.h7kz.proofstone.api.Matrix;
import io.github.h7kz.proofstone.api.Platform;
import io.github.h7kz.proofstone.api.Proofstone;
import io.github.h7kz.proofstone.api.Topology;

/**
 * Topology conformance (specs/self-tests.md section 1b, ADR-0016 section 7): P01 (join through the proxy), P02
 * (forwarding), P03 (switch), P04 (plugin messaging) and P05 (proxy command) run once per topology cell before the first
 * test of the cell, through {@code TopologyConformance}, and a red result makes every test on the cell INVALID, this one
 * included. So a green test here is the evidence that T00 and P01 to P05 passed on the cell; the run output has each
 * scenario's line. P04 needs the fixture plugin on the backends; this class installs it and checks it is there, so P04
 * cannot be skipped unseen on a conformance run.
 *
 * <p>Run one topology per invocation (M7 flake rule), for example
 * {@code -Dproofstone.cells=velocity@4.3.1+paper@26.3}. {@code :conformance:run} runs this class only when the selector names
 * a topology cell, and never otherwise (build.gradle.kts).
 */
// The fixture plugin (built and copied by conformance/build.gradle.kts) is what sends GetServer for P04.
@Proofstone(plugins = "build/fixture-plugins/*.jar")
@Matrix(platform = Platform.VELOCITY, versions = {"latest"})
@Matrix(platform = Platform.BUNGEECORD, versions = {"latest"})
@Topology.Backends({"lobby", "survival"})
class ConformanceTopologyTest {

    /**
     * What the agents report after P01 and P02 passed on a real proxy: {@code players} (P01 listed the player and its server)
     * and {@code events} (P01's probe saw the proxy connect it). The client is joined through the proxy by the harness.
     */
    @CellTest
    void theProxyAgentReportsPlayersAndEventsAndTheClientIsOnTheLobby(Cell cell, Topology topology, Client client) {
        assertThat(cell.platform()).isEqualTo("paper");
        assertThat(cell.capabilities()).contains(Capability.PROXY, Capability.TOPOLOGY);
        assertThat(topology.servers()).containsExactly("lobby", "survival");
        assertThat(topology.proxy().capabilities()).contains("proxy", "players", "events", "command", "log");
        assertThat(topology.server("lobby").player(client).name()).isEqualTo(client.name());
    }

    /** P04 ran on both backends' behalf only if the fixture is installed; a missing fixture would skip it unseen. */
    @CellTest
    void theFixtureThatP04NeedsIsInstalledOnEveryBackend(Topology topology) {
        for (String backend : topology.servers()) {
            assertThat(topology.server(backend).plugin("proofstone-fixture").config("greeting")).isEqualTo("hi");
        }
    }
}
