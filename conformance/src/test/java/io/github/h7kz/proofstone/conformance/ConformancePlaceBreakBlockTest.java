package io.github.h7kz.proofstone.conformance;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.h7kz.proofstone.api.Agent;
import io.github.h7kz.proofstone.api.BlockPos;
import io.github.h7kz.proofstone.api.CapturedEvent;
import io.github.h7kz.proofstone.api.CellTest;
import io.github.h7kz.proofstone.api.Client;
import io.github.h7kz.proofstone.api.EventProbe;
import io.github.h7kz.proofstone.api.Face;
import io.github.h7kz.proofstone.api.Item;
import io.github.h7kz.proofstone.api.Matrix;
import io.github.h7kz.proofstone.api.Platform;
import io.github.h7kz.proofstone.api.PlayerHandle;
import io.github.h7kz.proofstone.api.Proofstone;
import io.github.h7kz.proofstone.core.conformance.ConformanceFixture;

/**
 * C-B01 (specs/self-tests.md section 1, T-M11-04): {@code Client.placeBlock} and {@code Client.breakBlock} act as a player
 * and the server answers. The held stone is placed against the top face of the fixture ground: the server fires
 * {@code BlockPlaceEvent} and its world holds stone above it, where it held air. Breaking that block fires
 * {@code BlockBreakEvent} and leaves air.
 */
@Proofstone
@Matrix(platform = Platform.PAPER, versions = {"latest"})
class ConformancePlaceBreakBlockTest {

    private static final String WORLD = "world";
    /** Plain ground: right-clicking an anvil or chest opens it (a player must sneak to place against one), so the test places against stone. */
    private static final BlockPos GROUND = new BlockPos(1, 63, 0);
    private static final BlockPos ABOVE_GROUND = new BlockPos(GROUND.x(), GROUND.y() + 1, GROUND.z());
    private static final String PLACE = "org.bukkit.event.block.BlockPlaceEvent";
    private static final String BREAK = "org.bukkit.event.block.BlockBreakEvent";

    @CellTest
    void placeAgainstGroundThenBreakTheBlockAreSeenByTheServer(Agent agent, Client client) {
        PlayerHandle player = ConformanceFixture.stand(agent, client);
        agent.command("setblock " + GROUND.x() + " " + GROUND.y() + " " + GROUND.z() + " minecraft:stone");
        assertThat(agent.blockType(WORLD, GROUND)).as("ground to place against").isEqualTo("minecraft:stone");
        agent.command("gamemode creative " + client.name());
        player.give(Item.of("stone"));
        player.awaitState("a stone in the inventory", state -> !state.inventory().isEmpty());
        assertThat(agent.blockType(WORLD, ABOVE_GROUND)).as("before the place").isEqualTo("minecraft:air");

        try (EventProbe placed = agent.events().subscribe(PLACE)) {
            client.placeBlock(GROUND, Face.UP);

            CapturedEvent event = placed.awaitOne();
            assertThat(event.className()).isEqualTo(PLACE);
            assertThat(agent.blockType(WORLD, ABOVE_GROUND)).as("after the place").isEqualTo("minecraft:stone");
            assertThat(agent.blockType(WORLD, GROUND)).as("the block placed against is untouched").isEqualTo("minecraft:stone");
        }

        try (EventProbe broken = agent.events().subscribe(BREAK)) {
            client.breakBlock(ABOVE_GROUND);

            assertThat(broken.awaitOne().className()).isEqualTo(BREAK);
            assertThat(agent.blockType(WORLD, ABOVE_GROUND)).as("after the break").isEqualTo("minecraft:air");
        }
    }
}
