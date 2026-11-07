package dev.proofstone.conformance;

import static org.assertj.core.api.Assertions.assertThat;

import dev.proofstone.api.Agent;
import dev.proofstone.api.BlockPos;
import dev.proofstone.api.Click;
import dev.proofstone.api.Client;
import dev.proofstone.api.ItemView;
import dev.proofstone.api.PlayerHandle;
import dev.proofstone.api.Position;
import dev.proofstone.api.Window;
import java.util.List;
import java.util.function.Predicate;

/**
 * The vanilla spot the C03 to C06 scenarios build on: an anvil and a chest in reach of a joined player, on solid
 * ground. Flat worlds spawn underground and chunks load only around players (docs/platforms/paper.md), so the
 * blocks are placed after the join and the player is teleported last.
 */
final class ConformanceFixture {

    private static final int PLAYER_SLOTS = 36;
    static final BlockPos ANVIL = new BlockPos(0, 64, 2);
    static final BlockPos CHEST = new BlockPos(-1, 64, 0);
    /** C07 only: a grindstone, placed by {@link #placeBlock} because 1.12.2 has none (docs/platforms/paper.md). */
    static final BlockPos GRINDSTONE = new BlockPos(0, 64, -2);
    /** C08 only: a crafting table, placed by {@link #placeBlock}. */
    static final BlockPos CRAFTING_TABLE = new BlockPos(2, 64, 0);

    private ConformanceFixture() {
    }

    /**
     * Puts a block on solid ground next to the player, after {@link #stand}: a floor block under it, because a
     * grindstone breaks over air (docs/platforms/paper.md). The block is chosen by the scenario, not the fixture.
     */
    static void placeBlock(Agent agent, BlockPos pos, String block) {
        agent.command("setblock " + pos.x() + " " + (pos.y() - 1) + " " + pos.z() + " minecraft:stone");
        agent.command("setblock " + pos.x() + " " + pos.y() + " " + pos.z() + " minecraft:" + block);
    }

    /**
     * The first player-inventory slot of the window that the predicate accepts. The player's 36 slots trail every
     * window's own slots.
     *
     * @throws IllegalStateException when no player slot matches
     */
    static int playerSlot(Window window, Predicate<ItemView> predicate) {
        List<ItemView> slots = window.slots();
        for (int i = Math.max(0, slots.size() - PLAYER_SLOTS); i < slots.size(); i++) {
            if (predicate.test(slots.get(i))) {
                return i;
            }
        }
        throw new IllegalStateException("no matching player slot in " + window.type());
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

    /**
     * Puts the carried stack into the first empty slot of the player's own 36 slots, which trail every window's own
     * slots. Not {@code dropCursorIntoFirstEmptySlot}: that takes the anvil's empty input slot, and closing the anvil
     * returns that to the inventory on 26.3 but drops it on the ground on 1.8.8 (docs/platforms/paper.md).
     */
    static void placeCursorInInventory(Window window) {
        List<ItemView> slots = window.slots();
        for (int i = Math.max(0, slots.size() - PLAYER_SLOTS); i < slots.size(); i++) {
            if (slots.get(i).isEmpty()) {
                window.click(i, Click.LEFT);
                return;
            }
        }
        throw new IllegalStateException("no empty player slot in " + window.type());
    }

    /** The diamond sword, matched on either oracle's view. */
    static Predicate<ItemView> sword() {
        return item -> item.is("diamond_sword");
    }
}
