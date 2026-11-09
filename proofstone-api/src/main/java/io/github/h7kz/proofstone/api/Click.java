package io.github.h7kz.proofstone.api;

/**
 * A window click the driver can send (client-driver spec §2). The driver only sends the click; the result is
 * whatever the server syncs back, never a local prediction.
 */
public enum Click {
    LEFT,
    RIGHT,
    SHIFT_LEFT,
    SHIFT_RIGHT,
    NUMBER_1,
    NUMBER_2,
    NUMBER_3,
    NUMBER_4,
    NUMBER_5,
    NUMBER_6,
    NUMBER_7,
    NUMBER_8,
    NUMBER_9,
    /** The drop key (Q) over the slot: one item. */
    DROP,
    /** Ctrl and the drop key over the slot: the whole stack. */
    CTRL_DROP,
    /** Double click: gathers matching stacks onto the cursor. */
    DOUBLE,
    /** The swap-offhand key (F) over the slot. */
    SWAP_OFFHAND;

    /** The number-key click for hotbar position {@code n}, 1 to 9. */
    public static Click number(int n) {
        if (n < 1 || n > 9) {
            throw new IllegalArgumentException("number key must be 1..9, was " + n);
        }
        return values()[NUMBER_1.ordinal() + n - 1];
    }
}
