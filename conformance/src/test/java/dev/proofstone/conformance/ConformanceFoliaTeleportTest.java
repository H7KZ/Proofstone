package dev.proofstone.conformance;

import static org.assertj.core.api.Assertions.assertThat;

import dev.proofstone.api.Agent;
import dev.proofstone.api.CellTest;
import dev.proofstone.api.Capability;
import dev.proofstone.api.Client;
import dev.proofstone.api.DualAssert;
import dev.proofstone.api.Item;
import dev.proofstone.api.Matrix;
import dev.proofstone.api.PlayerHandle;
import dev.proofstone.api.Platform;
import dev.proofstone.api.Position;
import dev.proofstone.api.Proofstone;
import dev.proofstone.api.Requires;

/**
 * C-F01 (specs/self-tests.md §1, Folia pack): a client teleported into another region keeps its state. Folia moves a
 * player to the region that owns the destination through {@code teleportAsync}, and the agent waits for that future,
 * so the client and the server both read the new spot, and the inventory arrives with the player.
 *
 * <p>Only Folia has regions, so Paper skips the scenario (missing capability) and it needs {@link Capability#FOLIA_REGIONS}.
 * The destination is 512 blocks from the fixture, far beyond one region.
 */
@Proofstone
// Paper is declared so a paper@latest selection plans a cell (zero cells is a loud error); @Requires then makes the
// class a reasoned skip there, since only Folia has regions.
@Matrix(platform = Platform.PAPER, versions = {"latest"})
@Matrix(platform = Platform.FOLIA, versions = {"latest"})
@Matrix(platform = Platform.PURPUR, versions = {"latest"})
@Requires(Capability.FOLIA_REGIONS)
class ConformanceFoliaTeleportTest {

    private static final double FAR = 512.5;

    @CellTest
    void teleportAcrossRegionsKeepsThePlayerAndTheInventory(Agent agent, Client client) {
        PlayerHandle player = ConformanceFixture.stand(agent, client);
        player.give(Item.of("diamond_sword"));
        DualAssert.item(player, client).inInventory(ConformanceFixture.sword()).amount(1);

        agent.command("tp " + client.name() + " " + FAR + " 64 " + FAR);
        Position at = client.awaitPosition("teleport across regions to " + FAR, p -> p.x() == FAR && p.z() == FAR);
        assertThat(at.x()).isEqualTo(FAR);
        assertThat(at.z()).isEqualTo(FAR);

        // The sword must still be on both oracles after the move: the state followed the player to the new region.
        DualAssert.item(player, client).inInventory(ConformanceFixture.sword()).amount(1);
    }
}
