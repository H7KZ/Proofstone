package io.github.h7kz.proofstone.core.conformance;

import static org.assertj.core.api.Assertions.assertThat;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * T-M8-23: the server answers a shift-click on a crafting result with the result slot first and the player's own slot
 * after it, and the driver (like the vanilla client) ignores the packets of a window that is already closed. A scenario
 * that closes the window as soon as the result slot is empty therefore loses the late slot update and the client's
 * inventory never gets the item ({@code DISAGREE ... no matching item among []}, loader cells).
 *
 * <p>No Docker: a fake window whose own-slot update arrives {@value #LATE_MS} ms after the click, on another thread. The
 * delay models the network, it is not a wait of the scenario.
 */
class ConformanceCloseRaceTest {

    private static final int LATE_MS = 250;
    private static final int NEVER = -1;
    private static final int PLAYER_FIRST_SLOT = 10;
    private static final int PLAYER_SLOT_COUNT = 36;
    private static final int LAST_SLOT = PLAYER_FIRST_SLOT + PLAYER_SLOT_COUNT - 1;

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
    void c08WaitsForTheCraftedItemInTheWindowsOwnSlotsBeforeClosing() {
        World world = new World(LATE_MS);
        assertThatCode(() -> C08ShiftClickCraft.run(world.cell(), world.agent(), world.client()))
                .as("C08 when the player's slot update is late")
                .doesNotThrowAnyException();
    }

    @Test
    void anUpdateThatArrivesWithTheResultSlotNeedsNoWait() {
        World world = new World(0);
        assertThatCode(() -> C08ShiftClickCraft.run(world.cell(), world.agent(), world.client()))
                .doesNotThrowAnyException();
    }

    @Test
    void anUpdateThatNeverArrivesFailsLoudly() {
        World world = new World(NEVER);
        assertThatThrownBy(() -> C08ShiftClickCraft.run(world.cell(), world.agent(), world.client()))
                .isInstanceOf(AwaitTimeoutException.class);
    }

    /**
     * C05, C06 and C07 end by putting the result stack from the cursor into an empty player slot and closing the window.
     * A driver click returns on the first stateId change, which a late packet of the click before can cause, so the
     * click may return before the server's update for the slot arrived. The helper must wait for the slot itself.
     */
    @Test
    void placeCursorInInventoryReturnsOnlyWhenTheClickedSlotHoldsTheStack() {
        LateSlotWindow window = new LateSlotWindow();
        ConformanceFixture.placeCursorInInventory(window);
        assertThat(window.slots().subList(3, window.slots().size()))
                .as("the player's slots when the helper returned")
                .anyMatch(item -> item.is("diamond_sword"));
    }

    @Test
    void placeCursorInInventoryFailsLoudlyWhenTheSlotNeverGetsTheStack() {
        LateSlotWindow window = new LateSlotWindow(false);
        assertThatThrownBy(() -> ConformanceFixture.placeCursorInInventory(window))
                .isInstanceOf(AwaitTimeoutException.class);
    }

    /** A 3 + 36 slot window whose click returns at once and whose slot update comes {@value #LATE_MS} ms later. */
    private final class LateSlotWindow implements Window {

        private final List<ItemView> slots = new CopyOnWriteArrayList<>();
        private final boolean delivers;

        LateSlotWindow() {
            this(true);
        }

        LateSlotWindow(boolean delivers) {
            this.delivers = delivers;
            for (int i = 0; i < 3 + PLAYER_SLOT_COUNT; i++) {
                slots.add(ItemView.EMPTY);
            }
        }

        @Override
        public String type() {
            return "minecraft:anvil";
        }

        @Override
        public String title() {
            return "Anvil";
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
            throw new UnsupportedOperationException();
        }

        @Override
        public void click(int slot, Click click) {
            if (delivers) {
                network.schedule(() -> slots.set(slot, view("minecraft:diamond_sword", 1)), LATE_MS,
                        TimeUnit.MILLISECONDS);
            }
        }

        @Override
        public ItemView cursor() {
            return view("minecraft:diamond_sword", 1);
        }

        @Override
        public void moveFromInventory(Predicate<ItemView> predicate, int toSlot) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void dropCursorIntoFirstEmptySlot() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void rename(String name) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void close() {
        }
    }

    /** One server, one player and one crafting window, all in memory. */
    private final class World {

        private final long lateMs;
        private final Map<Integer, ItemView> serverInventory = new ConcurrentHashMap<>();
        private final List<ItemView> clientInventory = new CopyOnWriteArrayList<>();

        World(long lateMs) {
            this.lateMs = lateMs;
        }

        Cell cell() {
            return new Cell() {
                @Override
                public String platform() {
                    return "fabric";
                }

                @Override
                public String version() {
                    return "26.3";
                }

                @Override
                public String javaTag() {
                    return "25";
                }

                @Override
                public boolean bridged() {
                    return false;
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
                    serverInventory.clear();
                    clientInventory.clear();
                    return this;
                }

                @Override
                public PlayerHandle level(int level) {
                    return this;
                }

                @Override
                public PlayerHandle give(Item item) {
                    ItemView view = view(item.type(), item.amount());
                    serverInventory.put(0, view);
                    clientInventory.add(view);
                    return this;
                }

                @Override
                public PlayerState state() {
                    return new PlayerState("PSConf", 0, 0, "survival", 20, new HashMap<>(serverInventory),
                            ItemView.EMPTY);
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
                    return new CraftingWindow();
                }

                @Override
                public List<ItemView> inventory() {
                    return List.copyOf(clientInventory);
                }

                @Override
                public int experienceLevel() {
                    return 0;
                }
            };
        }

        /** The 46 slots of a crafting window: result, 3x3 grid, then the player's 36. */
        private final class CraftingWindow implements Window {

            private final List<ItemView> slots = new CopyOnWriteArrayList<>();
            private volatile boolean closed;
            private volatile ItemView cursor = ItemView.EMPTY;

            CraftingWindow() {
                for (int i = 0; i < PLAYER_FIRST_SLOT + PLAYER_SLOT_COUNT; i++) {
                    slots.add(ItemView.EMPTY);
                }
                for (int i = 0; i < clientInventory.size(); i++) {
                    slots.set(PLAYER_FIRST_SLOT + i, clientInventory.get(i));
                }
            }

            @Override
            public String type() {
                return "minecraft:crafting";
            }

            @Override
            public String title() {
                return "Crafting";
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

            @Override
            public synchronized void click(int slot, Click click) {
                if (closed) {
                    throw new IllegalStateException("window is closed");
                }
                if (slot >= PLAYER_FIRST_SLOT && click == Click.LEFT) {
                    cursor = slots.get(slot);
                    slots.set(slot, ItemView.EMPTY);
                } else if (slot >= 1 && slot <= 9 && click == Click.RIGHT) {
                    slots.set(slot, view(cursor.type(), 1));
                    cursor = cursor.amount() > 1 ? view(cursor.type(), cursor.amount() - 1) : ItemView.EMPTY;
                    if (cursor.isEmpty()) {
                        slots.set(0, view("minecraft:paper", 3));
                    }
                } else if (slot == 0 && click == Click.SHIFT_LEFT) {
                    for (int i = 0; i <= 9; i++) {
                        slots.set(i, ItemView.EMPTY);
                    }
                    ItemView paper = view("minecraft:paper", 3);
                    serverInventory.clear();
                    serverInventory.put(0, paper);
                    if (lateMs == 0) {
                        slots.set(LAST_SLOT, paper);
                    } else if (lateMs > 0) {
                        network.schedule(() -> deliverOwnSlot(paper), lateMs, TimeUnit.MILLISECONDS);
                    }
                }
            }

            /** The late packet: applied only while the window is open, as the driver does (McplSession.target). */
            private synchronized void deliverOwnSlot(ItemView paper) {
                if (!closed) {
                    slots.set(LAST_SLOT, paper);
                }
            }

            @Override
            public ItemView cursor() {
                return cursor;
            }

            @Override
            public void moveFromInventory(Predicate<ItemView> predicate, int toSlot) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void dropCursorIntoFirstEmptySlot() {
                throw new UnsupportedOperationException();
            }

            @Override
            public void rename(String name) {
                throw new UnsupportedOperationException();
            }

            /** Closing hands the window's own player slots back to the client's inventory (McplSession.retire). */
            @Override
            public synchronized void close() {
                if (closed) {
                    return;
                }
                closed = true;
                List<ItemView> kept = new ArrayList<>();
                for (int i = PLAYER_FIRST_SLOT; i < slots.size(); i++) {
                    if (!slots.get(i).isEmpty()) {
                        kept.add(slots.get(i));
                    }
                }
                clientInventory.clear();
                clientInventory.addAll(kept);
            }
        }
    }

    private static ItemView view(String type, int amount) {
        return new ItemView(type, amount, Map.of(), Map.of(), null, List.of(), 0, 0, null);
    }
}
