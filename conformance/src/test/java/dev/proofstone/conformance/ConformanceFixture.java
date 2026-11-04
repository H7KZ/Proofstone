package dev.proofstone.conformance;

import static org.assertj.core.api.Assertions.assertThat;

import dev.proofstone.api.Agent;
import dev.proofstone.api.BlockPos;
import dev.proofstone.api.Client;
import dev.proofstone.api.ItemView;
import dev.proofstone.api.PlayerHandle;
import dev.proofstone.api.Position;
import java.util.function.Predicate;

/**
 * The vanilla spot the C03 to C06 scenarios build on: an anvil and a chest in reach of a joined player, on solid
 * ground. Flat worlds spawn underground and chunks load only around players (docs/platforms/paper.md), so the
 * blocks are placed after the join and the player is teleported last.
 */
final class ConformanceFixture {

    static final BlockPos ANVIL = new BlockPos(0, 64, 2);
    static final BlockPos CHEST = new BlockPos(-1, 64, 0);

    private ConformanceFixture() {
    }

    /** Resets the player, builds the fixture and teleports the client onto it. Reset comes first (it teleports). */
    static PlayerHandle stand(Agent agent, Client client) {
        PlayerHandle player = agent.player(client).reset();
        player.awaitState("empty inventory", state -> state.inventory().isEmpty());
        agent.command("setblock 0 63 0 minecraft:stone");
        agent.command("setblock 0 63 2 minecraft:stone");
        agent.command("setblock 0 64 2 minecraft:anvil");
        agent.command("setblock -1 63 0 minecraft:stone");
        agent.command("setblock -1 64 0 minecraft:chest");
        agent.command("tp " + client.name() + " 0.5 64 0.5");
        Position at = client.awaitPosition("teleport to the fixture spot", p -> p.y() == 64.0);
        assertThat(at.x()).isEqualTo(0.5);
        return player;
    }

    /** The diamond sword, matched on either oracle's view. */
    static Predicate<ItemView> sword() {
        return item -> item.is("diamond_sword");
    }
}
