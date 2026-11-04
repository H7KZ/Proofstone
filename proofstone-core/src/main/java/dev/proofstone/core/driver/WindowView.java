package dev.proofstone.core.driver;

import java.util.List;

/**
 * A window model built only from server sync ({@code ContainerSetContent}, {@code ContainerSetSlot}); never from
 * a local prediction (client-driver spec §2). Each call returns the latest synced state.
 */
public interface WindowView extends ItemViews {

    /** Namespaced menu type, such as {@code minecraft:generic_9x3}; {@code minecraft:inventory} for window 0. */
    String type();

    /** The window id the server uses in container packets. */
    int id();

    /** The newest {@code stateId} the server sent for this window. */
    int stateId();

    /** All slots in server order: the container's own slots first, then the player's 36. A copy. */
    List<SlotStack> slots();

    /** The item on the cursor, as last synced by the server. */
    SlotStack cursor();
}
