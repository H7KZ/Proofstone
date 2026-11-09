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

    /** The item on the cursor, or {@link ItemView#EMPTY}. */
    ItemView cursor();

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
