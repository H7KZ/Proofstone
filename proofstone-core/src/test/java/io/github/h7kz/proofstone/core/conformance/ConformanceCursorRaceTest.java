package io.github.h7kz.proofstone.core.conformance;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.h7kz.proofstone.api.Agent;
import io.github.h7kz.proofstone.api.BlockPos;
import io.github.h7kz.proofstone.api.Cell;
import io.github.h7kz.proofstone.api.Click;
import io.github.h7kz.proofstone.api.Client;
import io.github.h7kz.proofstone.api.Events;
import io.github.h7kz.proofstone.api.Item;
import io.github.h7kz.proofstone.api.ItemView;
import io.github.h7kz.proofstone.api.PlayerHandle;
import io.github.h7kz.proofstone.api.PlayerState;
import io.github.h7kz.proofstone.api.PluginHandle;
import io.github.h7kz.proofstone.api.Position;
import io.github.h7kz.proofstone.api.Window;
import io.github.h7kz.proofstone.core.await.AwaitTimeoutException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * T-M7-21: on a bridged pre-1.17 row the server answers a pick-up click with a full resync that says "empty cursor",
 * and the real cursor follows as its own packet a few ms later, while the driver's click already returned on the
 * resync. A scenario that reads the cursor at once after a pick-up click (C04, C05) sees an empty cursor.
 *
 * <p>No Docker: a fake window whose cursor shows {@value #LATE_MS} ms after the pick-up click returned, on another
 * thread. The delay models the network, it is not a wait of the scenario.
 */
class ConformanceCursorRaceTest {

    private static final int LATE_MS = 250;
    private static final int NEVER = -1;
    private static final int PLAYER_SLOT_COUNT = 36;
    private static final int CHEST_SLOTS = 27;
    private static final int ANVIL_SLOTS = 3;

    private final ScheduledExecutorService network = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "fake-network");
        thread.setDaemon(true);
        return thread;
    });

    @AfterEach
    void stopNetwork() {
        network.shutdownNow();
    }

    @Test
    void c04WaitsForTheCursorAfterThePickUpClick() {
        World world = new World(LATE_MS, CHEST_SLOTS);
        assertThatCode(() -> C04ChestMove.run(world.cell(), world.agent(), world.client()))
                .as("C04 when the cursor packet is late")
                .doesNotThrowAnyException();
    }

    @Test
    void c05WaitsForTheCursorAfterThePickUpClick() {
        World world = new World(LATE_MS, ANVIL_SLOTS);
        assertThatCode(() -> C05AnvilRenameCost.run(world.cell(), world.agent(), world.client()))
                .as("C05 when the cursor packet is late")
                .doesNotThrowAnyException();
    }

    @Test
    void aCursorThatComesWithTheClickNeedsNoWait() {
        World chest = new World(0, CHEST_SLOTS);
        World anvil = new World(0, ANVIL_SLOTS);
        assertThatCode(() -> {
            C04ChestMove.run(chest.cell(), chest.agent(), chest.client());
            C05AnvilRenameCost.run(anvil.cell(), anvil.agent(), anvil.client());
        }).doesNotThrowAnyException();
    }

    @Test
    void aCursorThatNeverShowsStillFailsAtTheTimeout() {
        World world = new World(NEVER, CHEST_SLOTS);
        assertThatThrownBy(() -> C04ChestMove.run(world.cell(), world.agent(), world.client()))
                .isInstanceOf(AwaitTimeoutException.class);
    }

    /** One server, one player and one window with {@code ownSlots} slots of its own, all in memory. */
    private final class World {

        private final long lateMs;
        private final int ownSlots;
        private final Map<Integer, ItemView> serverInventory = new HashMap<>();
        private final List<ItemView> clientInventory = new CopyOnWriteArrayList<>();
        private volatile int level;

        World(long lateMs, int ownSlots) {
            this.lateMs = lateMs;
            this.ownSlots = ownSlots;
        }

        Cell cell() {
            return new Cell() {
                @Override
                public String platform() {
                    return "paper";
                }

                @Override
                public String version() {
                    return "1.8.8";
                }

                @Override
                public String javaTag() {
                    return "8";
                }

                @Override
                public boolean bridged() {
                    return true;
                }
            };
        }

        Agent agent() {
            PlayerHandle player = new PlayerHandle() {
                @Override
                public String name() {
                    return "PSConf";
                }

                @Override
                public PlayerHandle reset() {
                    synchronized (serverInventory) {
                        serverInventory.clear();
                    }
                    clientInventory.clear();
                    return this;
                }

                @Override
                public PlayerHandle level(int newLevel) {
                    level = newLevel;
                    return this;
                }

                @Override
                public PlayerHandle give(Item item) {
                    ItemView view = new ItemView(item.type(), item.amount(), item.enchants(), Map.of(),
                            item.name(), List.of(), 0, 0, null);
                    synchronized (serverInventory) {
                        serverInventory.put(serverInventory.size(), view);
                    }
                    clientInventory.add(view);
                    return this;
                }

                @Override
                public PlayerState state() {
                    synchronized (serverInventory) {
                        return new PlayerState("PSConf", level, 0, "survival", 20, new HashMap<>(serverInventory),
                                ItemView.EMPTY);
                    }
                }

                @Override
                public PlayerState awaitState(String description, Predicate<PlayerState> predicate) {
                    PlayerState state = state();
                    if (!predicate.test(state)) {
                        throw new AssertionError("fake agent: " + description);
                    }
                    return state;
                }
            };
            return new Agent() {
                @Override
                public String command(String command) {
                    return "";
                }

                @Override
                public PlayerHandle player(String name) {
                    return player;
                }

                @Override
                public PluginHandle plugin(String name) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public Events events() {
                    throw new UnsupportedOperationException();
                }

                @Override
                public List<String> capabilities() {
                    return List.of();
                }
            };
        }

        Client client() {
            return new Client() {
                @Override
                public String name() {
                    return "PSConf";
                }

                @Override
                public void command(String commandWithoutSlash) {
                }

                @Override
                public String awaitMessage(Pattern pattern) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public Position position() {
                    return new Position(0.5, 64, 0.5);
                }

                @Override
                public Position awaitPosition(String description, Predicate<Position> predicate) {
                    return position();
                }

                @Override
                public Window openBlock(BlockPos pos) {
                    return new PickUpWindow();
                }

                @Override
                public List<ItemView> inventory() {
                    return List.copyOf(clientInventory);
                }

                @Override
                public int experienceLevel() {
                    return level;
                }
            };
        }

        /** A chest (27 own slots) or an anvil (3), then the player's 36. */
        private final class PickUpWindow implements Window {

            private final List<ItemView> slots = new CopyOnWriteArrayList<>();
            private volatile ItemView cursor = ItemView.EMPTY;
            private boolean closed;

            PickUpWindow() {
                for (int i = 0; i < ownSlots + PLAYER_SLOT_COUNT; i++) {
                    slots.add(ItemView.EMPTY);
                }
            }

            @Override
            public String type() {
                return ownSlots == CHEST_SLOTS ? "minecraft:generic_9x3" : "minecraft:anvil";
            }

            @Override
            public String title() {
                return type();
            }

            @Override
            public List<ItemView> slots() {
                return List.copyOf(slots);
            }

            @Override
            public ItemView slot(int index) {
                return slots.get(index);
            }

            @Override
            public ItemView awaitSlot(int index, Predicate<ItemView> predicate) {
                ItemView item = slots.get(index);
                if (!predicate.test(item)) {
                    throw new AssertionError("fake window: slot " + index + " is " + item);
                }
                return item;
            }

            /** A pick-up empties the slot at once; the cursor follows later, as the 1.8.8 SetCursorItem does. */
            @Override
            public synchronized void click(int slot, Click click) {
                ItemView held = slots.get(slot);
                if (cursor.isEmpty() && !held.isEmpty()) {
                    slots.set(slot, ItemView.EMPTY);
                    if (ownSlots == ANVIL_SLOTS && slot == 2) {
                        slots.set(0, ItemView.EMPTY);
                        level--;
                    }
                    if (lateMs == 0) {
                        cursor = held;
                    } else if (lateMs > 0) {
                        network.schedule(() -> cursor = held, lateMs, TimeUnit.MILLISECONDS);
                    }
                } else if (!cursor.isEmpty() && held.isEmpty()) {
                    slots.set(slot, cursor);
                    cursor = ItemView.EMPTY;
                }
            }

            @Override
            public ItemView cursor() {
                return cursor;
            }

            @Override
            public void moveFromInventory(Predicate<ItemView> predicate, int toSlot) {
                for (ItemView item : clientInventory) {
                    if (predicate.test(item)) {
                        clientInventory.remove(item);
                        synchronized (serverInventory) {
                            serverInventory.clear();
                        }
                        slots.set(toSlot, item);
                        return;
                    }
                }
                throw new IllegalStateException("no matching item in the client inventory");
            }

            @Override
            public void dropCursorIntoFirstEmptySlot() {
                throw new UnsupportedOperationException();
            }

            @Override
            public void rename(String name) {
                ItemView sword = slots.get(0);
                slots.set(2, new ItemView(sword.type(), 1, Map.of(), Map.of(), name, List.of(), 0, 0, null));
            }

            /** Closing hands the window's own player slots back to the inventory (McplSession.retire). */
            @Override
            public synchronized void close() {
                if (closed) {
                    return;
                }
                closed = true;
                clientInventory.clear();
                for (int i = ownSlots; i < slots.size(); i++) {
                    if (!slots.get(i).isEmpty()) {
                        clientInventory.add(slots.get(i));
                    }
                }
                synchronized (serverInventory) {
                    serverInventory.clear();
                    for (ItemView item : clientInventory) {
                        serverInventory.put(serverInventory.size(), item);
                    }
                }
            }
        }
    }
}
