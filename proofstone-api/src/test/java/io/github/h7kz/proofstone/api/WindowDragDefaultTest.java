package io.github.h7kz.proofstone.api;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

/** A {@link Window} that cannot drag must say so (T-M11-09): returning quietly would read "nothing moved" as a pass. */
class WindowDragDefaultTest {

    private final Window window = new Window() {
        @Override public String type() { return "minecraft:generic_9x3"; }
        @Override public String title() { return "Chest"; }
        @Override public List<ItemView> slots() { return List.of(); }
        @Override public ItemView slot(int index) { return ItemView.EMPTY; }
        @Override public ItemView awaitSlot(int index, Predicate<ItemView> predicate) { return ItemView.EMPTY; }
        @Override public void click(int slot, Click click) { }
        @Override public ItemView cursor() { return ItemView.EMPTY; }
        @Override public void moveFromInventory(Predicate<ItemView> predicate, int toSlot) { }
        @Override public void dropCursorIntoFirstEmptySlot() { }
        @Override public void rename(String name) { }
        @Override public void close() { }
    };

    @Test
    void dragThrowsByDefault() {
        assertThatThrownBy(() -> window.drag(List.of(1, 2), DragMode.LEFT))
                .isInstanceOf(UnsupportedOperationException.class).hasMessageContaining("drag");
    }
}
