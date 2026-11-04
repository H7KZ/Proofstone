package dev.proofstone.driver.mcpl;

import java.util.List;

/**
 * The parsed view of a window's slots (T-M2-06), implemented next to {@code WindowView}. It parses on demand, so a
 * slot with an id the registries cannot name fails here with a clear message instead of on the network thread.
 */
public interface ItemViews {

    /** Every slot in the same order as {@code slots()}. */
    List<ItemView> itemViews();

    /** The cursor item. */
    ItemView cursorView();
}
