package io.github.h7kz.proofstone.examples.smoke.topology;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.h7kz.proofstone.api.CellTest;
import io.github.h7kz.proofstone.api.Client;
import io.github.h7kz.proofstone.api.DualAssert;
import io.github.h7kz.proofstone.api.Item;
import io.github.h7kz.proofstone.api.Matrix;
import io.github.h7kz.proofstone.api.Platform;
import io.github.h7kz.proofstone.api.Proofstone;
import io.github.h7kz.proofstone.api.Topology;

/**
 * A topology example (T-M7-14, docs/user/topologies.md): Velocity in front of two Paper backends. The client joins the
 * proxy and lands on {@code lobby}; {@code transferTo} is a server switch (the client types {@code /server survival}) and
 * waits for the client and the agents to agree. Each backend is the server oracle while the client is on it. The body is the
 * one that ran in M7 as {@code TopologyApiContract}, through the public API only.
 *
 * <p>Run it with {@code ./gradlew :examples:smoke:topologyExample -Dproofstone.cells=velocity@4.3.1+paper@26.3}.
 */
@Proofstone
@Matrix(platform = Platform.VELOCITY, versions = {"latest"})
@Topology.Backends({"lobby", "survival"})
class ServerSwitchE2E {

    @CellTest
    void eachBackendIsTheOracleWhileTheClientIsOnIt(Topology topology, Client client) {
        topology.server("lobby").player(client).give(Item.of("minecraft:apple").amount(3));
        DualAssert.item(topology.server("lobby").player(client), client).inInventory(i -> i.is("apple")).amount(3);

        client.transferTo("survival");
        topology.server("survival").player(client).give(Item.of("minecraft:stick").amount(2));
        DualAssert.item(topology.server("survival").player(client), client).inInventory(i -> i.is("stick")).amount(2);
        assertThat(client.inventory()).as("nothing of lobby's inventory on survival").noneMatch(i -> i.is("apple"));

        client.transferTo("lobby");
        DualAssert.item(topology.server("lobby").player(client), client).inInventory(i -> i.is("apple")).amount(3);
        assertThat(client.inventory()).as("nothing of survival's inventory back on lobby").noneMatch(i -> i.is("stick"));
    }
}
