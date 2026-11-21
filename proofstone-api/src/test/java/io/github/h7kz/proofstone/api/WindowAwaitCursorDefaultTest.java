package io.github.h7kz.proofstone.api;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

/** A {@link Window} that cannot await the cursor must say so (T-M11-11): returning quietly would read "never arrived" as a pass. */
class WindowAwaitCursorDefaultTest {

    private final Window window = new Window() {
        @Override public String type() { return "minecraft:anvil"; }
        @Override public String title() { return "Anvil"; }
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
    void awaitCursorThrowsByDefault() {
        assertThatThrownBy(() -> window.awaitCursor(item -> true))
                .isInstanceOf(UnsupportedOperationException.class).hasMessageContaining("awaitCursor");
    }
}
