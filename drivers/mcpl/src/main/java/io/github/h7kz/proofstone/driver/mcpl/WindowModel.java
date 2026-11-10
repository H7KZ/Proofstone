package io.github.h7kz.proofstone.driver.mcpl;

import io.github.h7kz.proofstone.api.ItemView;
import io.github.h7kz.proofstone.core.driver.SlotStack;
import io.github.h7kz.proofstone.core.driver.WindowView;
import java.util.ArrayList;
import java.util.List;
import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;

/**
 * One window's state, written only by server packets on the network thread and read by test threads. Separate
 * instances model the player inventory (id 0) and the open window, so one cannot overwrite the other.
 */
class WindowModel implements WindowView {

    private final int id;
    private final String type;
    private final String title;
    private List<SlotStack> slots;
    private int stateId;
    // Counts full resyncs (ContainerSetContent). Servers older than 1.17 have no state id, and ViaProxy then sends a
    // constant one, so "the state id moved" cannot be their sign of an answer. A single slot update is no sign
    // either: such a server sends it before the rest of its answer, and the next click must wait for the whole
    // answer, including the ping that ViaProxy turns the server's rejection into (T-M3-03).
    private int syncs;
    private SlotStack cursor = SlotStack.EMPTY;
    private boolean synced;
    private boolean consumed;
    private boolean closed;
    // The raw stacks, kept so item components are parsed on demand (T-M2-06) rather than on the network thread.
    private List<ItemStack> rawSlots = List.of();
    private ItemStack rawCursor;
    private final ItemParser parser;

    WindowModel(int id, String type, String title) {
        this(id, type, title, null);
    }

    /** @param registries names the ids in item stacks; {@code null} makes {@link #itemViews()} fail loudly */
    WindowModel(int id, String type, String title, ItemRegistries registries) {
        this.id = id;
        this.type = type;
        this.title = title;
        this.parser = registries == null ? null : new ItemParser(registries);
    }

    static SlotStack convert(ItemStack item) {
        // MCProtocolLib sends null for an empty slot.
        return item == null || item.getAmount() <= 0 ? SlotStack.EMPTY : new SlotStack(item.getId(), item.getAmount());
    }

    synchronized void setContent(int newStateId, ItemStack[] items, ItemStack carried) {
        List<SlotStack> next = new ArrayList<>(items.length);
        for (ItemStack item : items) {
            next.add(convert(item));
        }
        slots = next;
        rawSlots = new ArrayList<>(java.util.Arrays.asList(items));
        rawCursor = carried;
        stateId = newStateId;
        syncs++;
        cursor = convert(carried);
        synced = true;
    }

    /** Returns false when the slot cannot be applied, so the caller can record it instead of guessing. */
    synchronized boolean setSlot(int newStateId, int slot, ItemStack item) {
        if (slots == null || slot < 0 || slot >= slots.size()) {
            return false;
        }
        slots.set(slot, convert(item));
        rawSlots.set(slot, item);
        stateId = newStateId;
        return true;
    }

    synchronized void setCursor(ItemStack item) {
        cursor = convert(item);
        rawCursor = item;
    }

    @Override
    public synchronized List<ItemView> itemViews() {
        ItemParser itemParser = requireParser();
        List<ItemView> out = new ArrayList<>(rawSlots.size());
        for (ItemStack raw : rawSlots) {
            out.add(itemParser.parse(raw));
        }
        return List.copyOf(out);
    }

    @Override
    public synchronized ItemView cursorView() {
        return requireParser().parse(rawCursor);
    }

    private ItemParser requireParser() {
        if (parser == null) {
            throw new IllegalStateException("window " + id + " has no registries, so items cannot be parsed");
        }
        return parser;
    }

    /**
     * Takes the player's 36 slots from a closing container into slots 9..44 of this (window 0) model. While a
     * container is open the server syncs inventory changes to it, not to window 0, and it does not resend them on
     * close; the vanilla client avoids the staleness by sharing one inventory between menus. The items are what the
     * server sent, so this is not a prediction. Returns false when this model is not synced or the tail is short.
     */
    synchronized boolean adoptPlayerSlots(List<SlotStack> containerSlots, List<ItemStack> containerRaw) {
        int first = containerSlots.size() - PLAYER_SLOTS;
        if (slots == null || first < 0 || slots.size() < 9 + PLAYER_SLOTS
                || containerRaw.size() != containerSlots.size()) {
            return false;
        }
        for (int i = 0; i < PLAYER_SLOTS; i++) {
            slots.set(9 + i, containerSlots.get(first + i));
            // The ItemView reads come from the raw stacks, so both lists must take the adopted slots.
            rawSlots.set(9 + i, containerRaw.get(first + i));
        }
        return true;
    }

    /** The raw stacks as the server sent them, for another model to adopt. Null entries are empty slots. */
    synchronized List<ItemStack> rawSlots() {
        return java.util.Collections.unmodifiableList(new ArrayList<>(rawSlots));
    }

    private static final int PLAYER_SLOTS = 36;

    /**
     * Back to "not synced": a new server join (T-M7-09). The state id restarts and the cursor is empty; the next
     * full sync is the new server's. The {@code syncs} count is kept, so a click waiting across it still sees a change.
     */
    synchronized void reset() {
        slots = null;
        rawSlots = List.of();
        rawCursor = null;
        cursor = SlotStack.EMPTY;
        stateId = 0;
        synced = false;
    }

    synchronized void markClosed() {
        closed = true;
    }

    synchronized boolean isClosed() {
        return closed;
    }

    /** Hands the window out once, and only after its contents synced. */
    synchronized boolean claim() {
        if (!synced || consumed) {
            return false;
        }
        consumed = true;
        return true;
    }

    String title() {
        return title;
    }

    @Override
    public String type() {
        return type;
    }

    @Override
    public int id() {
        return id;
    }

    /** How many full content resyncs the server has sent for this window. */
    synchronized int syncs() {
        return syncs;
    }

    @Override
    public synchronized int stateId() {
        return stateId;
    }

    @Override
    public synchronized List<SlotStack> slots() {
        return slots == null ? List.of() : List.copyOf(slots);
    }

    @Override
    public synchronized SlotStack cursor() {
        return cursor;
    }
}
