package dev.proofstone.core.driver;

import dev.proofstone.api.Click;

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
