package dev.proofstone.core.driver;

/**
 * An open container window (client-driver spec §1). Clicking is added by T-M2-05.
 *
 * <p>SEAM: {@code click} and the awaiting variant of {@link #close()} join in T-M2-05.
 */
public interface WindowSession extends WindowView {

    /** The window title as plain text. */
    String title();

    /** Tells the server this client closed the window. It does not wait for a resync yet (T-M2-05). */
    void close();
}
