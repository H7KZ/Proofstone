package io.github.h7kz.proofstone.core.driver;

import io.github.h7kz.proofstone.api.Click;
import io.github.h7kz.proofstone.api.DragMode;
import java.util.List;

/**
 * An open container window (client-driver spec §1).
 */
public interface WindowSession extends WindowView {

    /** The window title as plain text. */
    String title();

    /**
     * Sends the click and returns once the server answered it with a sync carrying a different {@code stateId}
     * (spec §2). The slots and cursor are then the server's, never a prediction. A click the server treats as a
     * no-op produces no sync and fails with {@link ClientDriverException} after the wait.
     */
    void click(int slot, Click click);

    /**
     * Drags the carried stack across the slots: a quick-craft start, one add per slot, an end. Returns once the server
     * answered the end with a sync (as {@link #click}). Additive (T-M11-09); a driver without support fails loudly.
     *
     * @throws UnsupportedOperationException when the driver cannot drag (the rule on {@link ClientSession})
     * @throws ClientDriverException when the window is closed or the server never answers
     */
    default void drag(List<Integer> slots, DragMode mode) {
        throw new UnsupportedOperationException("this driver cannot drag across slots; use a driver that supports it");
    }

    /**
     * Tells the server this client closed the window. The server does not acknowledge a client close, so this
     * returns after sending. Items the server moves afterwards (a returned cursor) arrive later as syncs of
     * window 0: await the inventory state you expect instead of reading it straight away.
     */
    void close();

    /**
     * Types the anvil rename box (ServerboundRenameItemPacket). The server answers by updating the result slot, not
     * with a click sync, so the caller awaits the slot it expects.
     *
     * @throws ClientDriverException when the window is not an anvil or is closed
     */
    void rename(String name);
}
