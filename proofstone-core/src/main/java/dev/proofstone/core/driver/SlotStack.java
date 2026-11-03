package dev.proofstone.core.driver;

/**
 * One slot as the server sent it. The item is the protocol's registry id; the driver does not resolve it to a
 * namespaced key or read data components yet (T-M2-06 maps this to {@code ItemView}). An empty slot has amount 0.
 */
public record SlotStack(int registryId, int amount) {

    /** The empty slot. */
    public static final SlotStack EMPTY = new SlotStack(0, 0);

    public boolean isEmpty() {
        return amount <= 0;
    }
}
