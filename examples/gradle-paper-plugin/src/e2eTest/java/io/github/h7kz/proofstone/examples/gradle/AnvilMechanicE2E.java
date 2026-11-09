package io.github.h7kz.proofstone.examples.gradle;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.h7kz.proofstone.api.Agent;
import io.github.h7kz.proofstone.api.BlockPos;
import io.github.h7kz.proofstone.api.CellTest;
import io.github.h7kz.proofstone.api.Client;
import io.github.h7kz.proofstone.api.Item;
import io.github.h7kz.proofstone.api.ItemView;
import io.github.h7kz.proofstone.api.Matrix;
import io.github.h7kz.proofstone.api.PlayerHandle;
import io.github.h7kz.proofstone.api.Platform;
import io.github.h7kz.proofstone.api.Position;
import io.github.h7kz.proofstone.api.Proofstone;
import io.github.h7kz.proofstone.api.Window;
import java.util.Set;

/**
 * The anvil mechanic on a real Paper 26.3 server (T-M6-04). Each test starts from the default config, where both
 * injected bugs are off. {@code -Dproofstone.bug=<name>} turns one bug on for every test, so the suite must go red.
 */
@Proofstone(plugins = "build/libs/anvil-mechanic.jar")
@Matrix(platform = Platform.PAPER, versions = {"26.3"})
class AnvilMechanicE2E {

    private static final BlockPos ANVIL = new BlockPos(0, 64, 2);
    private static final Set<String> BUGS = Set.of("double-output", "accept-any-right");

    @CellTest
    void ironAndGoldOnTheAnvilMakeOneDiamond(Agent agent, Client client) {
        injectRequestedBug(agent);
        PlayerHandle player = standNextToAnAnvil(agent, client);
        player.give(Item.of("iron_ingot")).give(Item.of("gold_ingot"));

        try (Window anvil = client.openBlock(ANVIL)) {
            assertThat(anvil.type()).isEqualTo("minecraft:anvil");
            anvil.moveFromInventory(item -> item.is("iron_ingot"), 0);
            anvil.moveFromInventory(item -> item.is("gold_ingot"), 1);

            ItemView result = anvil.awaitSlot(2, item -> item.is("diamond"));
            assertThat(result.amount()).as("one iron and one gold make one diamond").isEqualTo(1);
        }
    }

    @CellTest
    void coalOnTheRightMakesNothing(Agent agent, Client client) {
        injectRequestedBug(agent);
        PlayerHandle player = standNextToAnAnvil(agent, client);
        player.give(Item.of("iron_ingot")).give(Item.of("coal"));

        try (Window anvil = client.openBlock(ANVIL)) {
            anvil.moveFromInventory(item -> item.is("iron_ingot"), 0);
            anvil.moveFromInventory(item -> item.is("coal"), 1);
            // The server has taken the coal into the anvil, and the anvil prepares its result in that same click, so
            // the result slot is settled by the time the server reports the move (the v0 API has no tick wait).
            player.awaitState("coal moved into the anvil",
                    state -> state.inventory().values().stream().noneMatch(item -> item.is("coal")));

            assertThat(anvil.slot(2).isEmpty()).as("coal is not an accepted right input").isTrue();
        }
    }

    /** Turns on the bug named by {@code -Dproofstone.bug}, if any. An unknown name fails the run, never passes it. */
    private static void injectRequestedBug(Agent agent) {
        String bug = System.getProperty("proofstone.bug", "");
        if (bug.isEmpty()) {
            return;
        }
        if (!BUGS.contains(bug)) {
            throw new IllegalStateException("unknown proofstone.bug '" + bug + "', expected one of " + BUGS);
        }
        agent.plugin("AnvilMechanic").patchConfig("bugs." + bug, true);
    }

    /** Joins the world at a spot with ground, an anvil in reach and an empty inventory. */
    private static PlayerHandle standNextToAnAnvil(Agent agent, Client client) {
        // Reset first: it also teleports the player to the fixture spot, which would undo the teleport below.
        PlayerHandle player = agent.player(client).reset();
        player.awaitState("empty inventory", state -> state.inventory().isEmpty());
        // Chunks load around players only, and flat worlds spawn underground (docs/platforms/paper.md).
        agent.command("setblock 0 63 0 minecraft:stone");
        agent.command("setblock 0 63 2 minecraft:stone");
        agent.command("setblock 0 64 2 minecraft:anvil");
        agent.command("tp " + client.name() + " 0.5 64 0.5");
        Position at = client.awaitPosition("teleport to the anvil", p -> p.y() == 64.0);
        assertThat(at.x()).isEqualTo(0.5);
        return player;
    }
}
