package dev.proofstone.driver.mcpl;

import dev.proofstone.core.driver.SlotStack;
import dev.proofstone.core.driver.WindowView;
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
    private SlotStack cursor = SlotStack.EMPTY;
    private boolean synced;
    private boolean consumed;
    private boolean closed;

    WindowModel(int id, String type, String title) {
        this.id = id;
        this.type = type;
        this.title = title;
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
        stateId = newStateId;
        cursor = convert(carried);
        synced = true;
    }

    /** Returns false when the slot cannot be applied, so the caller can record it instead of guessing. */
    synchronized boolean setSlot(int newStateId, int slot, ItemStack item) {
        if (slots == null || slot < 0 || slot >= slots.size()) {
            return false;
        }
        slots.set(slot, convert(item));
        stateId = newStateId;
        return true;
    }

    synchronized void setCursor(ItemStack item) {
        cursor = convert(item);
    }

    /**
     * Takes the player's 36 slots from a closing container into slots 9..44 of this (window 0) model. While a
     * container is open the server syncs inventory changes to it, not to window 0, and it does not resend them on
     * close; the vanilla client avoids the staleness by sharing one inventory between menus. The items are what the
     * server sent, so this is not a prediction. Returns false when this model is not synced or the tail is short.
     */
    synchronized boolean adoptPlayerSlots(List<SlotStack> containerSlots) {
        int first = containerSlots.size() - PLAYER_SLOTS;
        if (slots == null || first < 0 || slots.size() < 9 + PLAYER_SLOTS) {
            return false;
        }
        for (int i = 0; i < PLAYER_SLOTS; i++) {
            slots.set(9 + i, containerSlots.get(first + i));
        }
        return true;
    }

    private static final int PLAYER_SLOTS = 36;

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
