package io.github.h7kz.proofstone.core.conformance;

import io.github.h7kz.proofstone.api.Agent;
import io.github.h7kz.proofstone.api.BlockPos;
import io.github.h7kz.proofstone.api.Click;
import io.github.h7kz.proofstone.api.Client;
import io.github.h7kz.proofstone.api.ItemView;
import io.github.h7kz.proofstone.api.PlayerHandle;
import io.github.h7kz.proofstone.api.Position;
import io.github.h7kz.proofstone.api.Window;
import io.github.h7kz.proofstone.core.await.Await;
import java.time.Duration;
import java.util.List;
import java.util.function.Predicate;
import org.junit.jupiter.api.Assertions;

/**
 * The vanilla spot the C03 to C08 scenarios build on: an anvil and a chest in reach of a joined player, on solid
 * ground. Flat worlds spawn underground and chunks load only around players (docs/platforms/paper.md), so the
 * blocks are placed after the join and the player is teleported last.
 *
 * <p>Lives in core, not in the conformance module, so the per-cell check (self-tests section 1) can run the same
 * scenarios as {@code :conformance:run} (T-M4-10). Assertions are JUnit's: core has no AssertJ.
 */
public final class ConformanceFixture {

    private static final int PLAYER_SLOTS = 36;
    /** The same wait budget as every other client wait (DualAssert.DEFAULT_TIMEOUT). */
    private static final Duration AWAIT = Duration.ofSeconds(10);
    static final BlockPos ANVIL = new BlockPos(0, 64, 2);
    public static final BlockPos CHEST = new BlockPos(-1, 64, 0);
    /** C07 only: a grindstone, placed by {@link #placeBlock} because 1.12.2 has none (docs/platforms/paper.md). */
    static final BlockPos GRINDSTONE = new BlockPos(0, 64, -2);
    /** C08 only: a crafting table, placed by {@link #placeBlock}. */
    public static final BlockPos CRAFTING_TABLE = new BlockPos(2, 64, 0);

    private ConformanceFixture() {
    }

    /**
     * Puts a block on solid ground next to the player, after {@link #stand}: a floor block under it, because a
     * grindstone breaks over air (docs/platforms/paper.md). The block is chosen by the scenario, not the fixture.
     */
    public static void placeBlock(Agent agent, BlockPos pos, String block) {
        agent.command("setblock " + pos.x() + " " + (pos.y() - 1) + " " + pos.z() + " minecraft:stone");
        agent.command("setblock " + pos.x() + " " + pos.y() + " " + pos.z() + " minecraft:" + block);
    }

    /**
     * The first player-inventory slot of the window that the predicate accepts. The player's 36 slots trail every
     * window's own slots.
     *
     * @throws IllegalStateException when no player slot matches
     */
    public static int playerSlot(Window window, Predicate<ItemView> predicate) {
        List<ItemView> slots = window.slots();
        for (int i = Math.max(0, slots.size() - PLAYER_SLOTS); i < slots.size(); i++) {
            if (predicate.test(slots.get(i))) {
                return i;
            }
        }
        throw new IllegalStateException("no matching player slot in " + window.type());
    }

    /** Resets the player, builds the fixture and teleports the client onto it. Reset comes first (it teleports). */
    public static PlayerHandle stand(Agent agent, Client client) {
        PlayerHandle player = agent.player(client).reset();
        player.awaitState("empty inventory", state -> state.inventory().isEmpty());
        agent.command("setblock 0 63 0 minecraft:stone");
        agent.command("setblock 0 63 2 minecraft:stone");
        agent.command("setblock 0 64 2 minecraft:anvil");
        agent.command("setblock -1 63 0 minecraft:stone");
        agent.command("setblock -1 64 0 minecraft:chest");
        agent.command("tp " + client.name() + " 0.5 64 0.5");
        Position at = client.awaitPosition("teleport to the fixture spot", p -> p.y() == 64.0);
        Assertions.assertEquals(0.5, at.x(), "x after the teleport to the fixture spot");
        return player;
    }

    /**
     * Puts the carried stack into the first empty slot of the player's own 36 slots, which trail every window's own
     * slots, and returns once the client's own view of that slot holds a stack. Not {@code dropCursorIntoFirstEmptySlot}:
     * that takes the anvil's empty input slot, and closing the anvil returns that to the inventory on 26.3 but drops it
     * on the ground on 1.8.8 (docs/platforms/paper.md).
     *
     * <p>The wait is the point (T-M8-23): a click returns on the first stateId change, which can be a late packet of
     * the click before, so it may return before the server's update for this slot arrived. A close sent in that gap makes
     * the driver, like the vanilla client, ignore the update, and the inventory never gets the stack.
     */
    static void placeCursorInInventory(Window window) {
        List<ItemView> slots = window.slots();
        for (int i = Math.max(0, slots.size() - PLAYER_SLOTS); i < slots.size(); i++) {
            if (slots.get(i).isEmpty()) {
                int target = i;
                window.click(target, Click.LEFT);
                awaitPlayerSlot(window, "the carried stack in player slot " + target + " of " + window.type(),
                        slot -> !slot.isEmpty(), target);
                return;
            }
        }
        throw new IllegalStateException("no empty player slot in " + window.type());
    }

    /**
     * Waits until the client's view of one slot of the window satisfies the predicate. A scenario calls this before it
     * closes a window whose last click the server answers with more than one packet (T-M8-23).
     *
     * @throws io.github.h7kz.proofstone.core.await.AwaitTimeoutException when it never does within the budget
     */
    static void awaitPlayerSlot(Window window, String description, Predicate<ItemView> predicate, int slot) {
        Await.until(description, () -> window.slots().get(slot), predicate, AWAIT);
    }

    /**
     * Waits until some one of the player's own 36 slots, in the client's view of the window, satisfies the predicate.
     * The server sends a craft's result slot first and the player's slot after it, so this is the wait that must come
     * before the close (T-M8-23).
     */
    public static void awaitInPlayerSlots(Window window, String description, Predicate<ItemView> predicate) {
        Await.until(description, () -> {
            List<ItemView> slots = window.slots();
            return slots.subList(Math.max(0, slots.size() - PLAYER_SLOTS), slots.size()).stream().anyMatch(predicate);
        }, found -> found, AWAIT);
    }

    /** The diamond sword, matched on either oracle's view. */
    public static Predicate<ItemView> sword() {
        return item -> item.is("diamond_sword");
    }
}
