package io.github.h7kz.proofstone.api;

/**
 * The mouse button held while dragging a stack across slots ({@link Window#drag}). The server decides what each mode
 * does with the carried stack, so a test reads the result from the slots.
 */
public enum DragMode {
    /** Left button: the carried stack is split evenly across the slots. */
    LEFT,
    /** Right button: one item into each slot. */
    RIGHT,
    /** Middle button: fills each slot to its full stack size; creative mode only. */
    MIDDLE
}
