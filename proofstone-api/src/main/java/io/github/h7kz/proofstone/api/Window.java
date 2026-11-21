package io.github.h7kz.proofstone.api;

import java.util.List;
import java.util.function.Predicate;

/**
 * An open container window as the client sees it (test-api spec section 4). Every read is what the server last
 * synced; every click is answered by the server before it returns, never predicted. Closing it tells the server.
 */
public interface Window extends AutoCloseable {

    /** The namespaced menu type, such as {@code minecraft:anvil}. */
    String type();

    /** The window title as plain text. */
    String title();

    /** Every slot in server order: the container's own slots, then the player's 36. */
    List<ItemView> slots();

    /** The item in the slot, as last synced. */
    ItemView slot(int index);

    /**
     * Waits for the slot to hold an item the predicate accepts, for the default timeout.
     *
     * @throws RuntimeException on timeout, with the slot's last contents
     */
    ItemView awaitSlot(int index, Predicate<ItemView> predicate);

    /** Clicks the slot and returns once the server answered. */
    void click(int slot, Click click);

    /**
     * Drags the carried stack across the slots as a player holding a mouse button: a start, one add per slot in the
     * order given, and an end, then returns once the server answered the end. The server distributes the stack (a left
     * drag splits it evenly, a right drag puts one in each slot), so read the slots and the cursor afterwards.
     *
     * <p>Additive (T-M11-09). The default throws, so a custom {@code Window} fails loudly instead of passing. A driver
     * that cannot drag (Bedrock, the reference Mineflayer driver) fails the same way.
     *
     * @param slots window slot indexes, at least one, each once
     * @throws IllegalArgumentException when {@code slots} is empty, repeats a slot or names one outside the window
     * @throws UnsupportedOperationException when this window or its driver cannot drag (every driver, one type)
     * @throws RuntimeException when the server never answers, with the reason
     */
    default void drag(List<Integer> slots, DragMode mode) {
        throw new UnsupportedOperationException("this Window implementation cannot drag; override drag");
    }

    /** The item on the cursor, or {@link ItemView#EMPTY}, as last synced: a one-shot read, see {@link #awaitCursor}. */
    ItemView cursor();

    /**
     * Waits for the cursor to hold an item the predicate accepts, for the default timeout, and returns it. Use it
     * after a click whose result the server delivers on the cursor: the click returns on the first window sync, and the
     * cursor can arrive in a packet just after it, so a one-shot {@link #cursor()} may still read the old value.
     * Mirrors {@link #awaitSlot}.
     *
     * <p>Additive (T-M11-11). The default throws, so a custom {@code Window} fails loudly instead of passing.
     *
     * @throws RuntimeException on timeout, with the cursor's last contents
     */
    default ItemView awaitCursor(Predicate<ItemView> predicate) {
        throw new UnsupportedOperationException("this Window implementation cannot await the cursor; override awaitCursor");
    }

    /**
     * Picks up the first player-inventory item the predicate accepts and places it in {@code toSlot}: two clicks, each
     * answered by the server.
     *
     * @throws IllegalStateException when no item matches
     */
    void moveFromInventory(Predicate<ItemView> predicate, int toSlot);

    /** Puts the cursor item into the first empty slot of the window. */
    void dropCursorIntoFirstEmptySlot();

    /**
     * Types the name into an anvil's rename box, as the vanilla client does. The server answers through the result
     * slot, so await that slot; the call returns once the name is sent.
     *
     * @throws IllegalStateException when the window is not an anvil
     */
    void rename(String name);

    /** Tells the server the client closed the window. The server does not acknowledge a client close. */
    @Override
    void close();
}
